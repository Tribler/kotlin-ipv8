package nl.tudelft.ipv8.android.messaging.bluetooth

import android.bluetooth.*
import android.content.Context
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import mu.KotlinLogging
import nl.tudelft.ipv8.Peer
import nl.tudelft.ipv8.keyvault.defaultCryptoProvider
import nl.tudelft.ipv8.messaging.Packet
import nl.tudelft.ipv8.messaging.SERIALIZED_PUBLIC_KEY_SIZE
import nl.tudelft.ipv8.messaging.bluetooth.BluetoothAddress
import nl.tudelft.ipv8.messaging.bluetooth.BluetoothEndpoint
import nl.tudelft.ipv8.peerdiscovery.Network
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

private val logger = KotlinLogging.logger {}

class BluetoothLeEndpoint(
    private val context: Context,
    private val bluetoothManager: BluetoothManager,
    private val gattServer: IPv8GattServer,
    private val bleAdvertiser: IPv8BluetoothLeAdvertiser,
    private val network: Network
) : BluetoothEndpoint() {
    /**
     * True if we are currently advertising and sending packets to connected GATT servers.
     */
    private var isOpen = false

    /**
     * The map of connected GATT servers to whose we can send messages.
     */
    private val gatts: MutableMap<BluetoothAddress, BluetoothGatt> = mutableMapOf()

    /**
     * We can only send one message at a time to a GATT server, so we create a buffer of messages
     * to be sent using a channel. In the future, we should create a separate buffer for each
     * device, so we can communicate with multiple devices in parallel.
     */
    private val buffer = Channel<Pair<BluetoothAddress, ByteArray>>(100)

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var sendJob: Job? = null
    private var sendContinuation: Continuation<Boolean>? = null

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            logger.debug { "onConnectionStateChange: ${gatt.device.address} $status $newState" }

            if (status == BluetoothGatt.GATT_SUCCESS) {
                if (newState == BluetoothGatt.STATE_CONNECTED) {
                    logger.debug { "Discovering services for ${gatt.device.address}" }
                    gatt.discoverServices()
                } else if (newState == BluetoothGatt.STATE_DISCONNECTED) {
                    // TODO: Add a timeout before we close the GATT connection as this can probably
                    //  be just a temporary drop
                    // disconnect only disconnects the device, close releases all resources
                    gatt.close()
                    gatts.remove(BluetoothAddress(gatt.device.address))
                    network.removeByAddress(BluetoothAddress(gatt.device.address))
                }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            logger.debug { "onServicesDiscovered $status" }

            if (status == BluetoothGatt.GATT_SUCCESS) {
                val service = gatt.getService(IPv8GattServer.SERVICE_UUID)
                if (service != null) {
                    logger.debug { "Enabling notifications" }
                    val readCharacteristic = service
                        .getCharacteristic(IPv8GattServer.READ_CHARACTERISTIC_UUID)
                    gatt.setCharacteristicNotification(readCharacteristic, true)

                    getGattIdentity(gatt)
                } else {
                    logger.error { "Gatt service is null" }
                }
            }
        }

        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                logger.debug { "onCharacteristicRead: $characteristic" }
                if (characteristic.uuid == IPv8GattServer.IDENTITY_CHARACTERISTIC_UUID) {
                    val publicKeyBin = characteristic.value
                    val peer = Peer(
                        bluetoothAddress = BluetoothAddress(gatt.device.address),
                        key = defaultCryptoProvider.keyFromPublicBin(publicKeyBin)
                    )
                    logger.debug { "adding verified peer: " + peer.mid }
                    network.addVerifiedPeer(peer)
                } else if (characteristic.uuid == IPv8GattServer.READ_CHARACTERISTIC_UUID) {
                    // TODO: how to find the data size?
                    val data = characteristic.value
                    val packet = Packet(BluetoothAddress(gatt.device.address), data)
                    notifyListeners(packet)
                }
            }
        }

        override fun onCharacteristicWrite(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                logger.debug { "Characteristic write success: ${gatt.device.address}" }
            } else {
                logger.error { "Characteristic write status: ${gatt.device.address} $status" }
            }
            sendContinuation?.resume(status == BluetoothGatt.GATT_SUCCESS)
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            logger.debug { "onCharacteristicChanged: ${characteristic.value}" }
            val read = gatt.readCharacteristic(characteristic)
            logger.debug { "read request sent? $read" }
        }
    }

    override fun isOpen(): Boolean {
        return isOpen
    }

    override fun send(address: BluetoothAddress, data: ByteArray) {
        logger.debug { "Send to $address: ${data.size} B" }
        buffer.offer(Pair(address, data))
    }

    private suspend fun sendToGatt(address: BluetoothAddress, data: ByteArray) {
        val gatt = gatts[address]
        if (gatt != null) {
            suspendCoroutine<Boolean> { continuation ->
                sendContinuation = continuation

                val service = gatt.getService(IPv8GattServer.SERVICE_UUID)
                val characteristic = service
                    .getCharacteristic(IPv8GattServer.WRITE_CHARACTERISTIC_UUID)
                characteristic.value = data
                val success = gatt.writeCharacteristic(characteristic)
                if (success) {
                    logger.debug { "Write characteristics: $address ${data.size} B" }
                } else {
                    logger.error { "Characteristic write failed: $address ${data.size} B" }
                }
            }
        } else {
            logger.error { "GATT for address not found: $address" }
        }
    }

    override fun open() {
        gattServer.open()
        gattServer.packetListener = this::notifyListeners
        gattServer.connectionListener = { device, newState ->
            if (newState == BluetoothGatt.STATE_CONNECTED) {
                // Connect back to the device that connected to the server
                // TODO: Could we reuse the existing connection by using characteristic change notifications?
                connectTo(BluetoothAddress(device.address))
            }
        }
        bleAdvertiser.start()
        isOpen = true

        sendJob = scope.launch {
            while (isActive) {
                val (address, data) = buffer.receive()
                sendToGatt(address, data)
            }
        }
    }

    override fun close() {
        bleAdvertiser.stop()
        gattServer.close()
        gattServer.packetListener = null
        gattServer.connectionListener = null
        isOpen = false
        sendJob?.cancel()
        sendJob = null

        // Disconnect from all GATT servers
        for (gatt in gatts.values) {
            gatt.close()
        }
        gatts.clear()
    }

    override fun connectTo(address: BluetoothAddress) {
        val gatt = gatts[address]
        if (gatt == null) {
            logger.debug { "Connect to $address" }
            val device = bluetoothManager.adapter.getRemoteDevice(address.mac)
            val newGatt = device.connectGatt(context, false, gattCallback)
            gatts[address] = newGatt
        } else {
            logger.warn { "Already connected to $address" }
            getGattIdentity(gatt)
        }
    }

    private fun getGattIdentity(gatt: BluetoothGatt) {
        val service = gatt.getService(IPv8GattServer.SERVICE_UUID)
        if (service != null) {
            // Get public key
            val identityCharacteristic = service.getCharacteristic(
                IPv8GattServer.IDENTITY_CHARACTERISTIC_UUID
            )
            if (identityCharacteristic != null) {
                logger.debug { "Reading identity characteristics" }
                gatt.readCharacteristic(identityCharacteristic)
            } else {
                logger.error { "Identity characteristic is null" }
            }
        } else {
            logger.error { "Gatt service is null" }
            gatt.discoverServices()
        }
    }
}
