package nl.tudelft.ipv8.android.messaging.bluetooth

import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothManager
import android.content.Context
import mu.KotlinLogging
import nl.tudelft.ipv8.Peer
import nl.tudelft.ipv8.keyvault.defaultCryptoProvider
import nl.tudelft.ipv8.messaging.SERIALIZED_PUBLIC_KEY_SIZE
import nl.tudelft.ipv8.messaging.bluetooth.BluetoothAddress
import nl.tudelft.ipv8.messaging.bluetooth.BluetoothEndpoint
import nl.tudelft.ipv8.peerdiscovery.Network

private val logger = KotlinLogging.logger {}

class BluetoothLeEndpoint(
    private val context: Context,
    private val bluetoothManager: BluetoothManager,
    private val gattServer: IPv8GattServer,
    private val bleAdvertiser: IPv8BluetoothLeAdvertiser,
    private val network: Network
) : BluetoothEndpoint() {
    private var isOpen = false

    private val gatts: MutableMap<BluetoothAddress, BluetoothGatt> = mutableMapOf()

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            logger.debug { "onConnectionStateChange: ${gatt.device.address} $status $newState" }

            if (status == BluetoothGatt.GATT_SUCCESS) {
                if (newState == BluetoothGatt.STATE_CONNECTED) {
                    logger.debug { "Discovering services for ${gatt.device.address}" }
                    gatt.discoverServices()
                }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            logger.debug { "onServicesDiscovered $status" }

            if (status == BluetoothGatt.GATT_SUCCESS) {
                val service = gatt.getService(IPv8GattServer.SERVICE_UUID)
                if (service != null) {
                    logger.debug { "Enabling notifications" }
                    val characteristic = service
                        .getCharacteristic(IPv8GattServer.CHARACTERISTIC_UUID)
                    gatt.setCharacteristicNotification(characteristic, true)

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
                    logger.debug { "service is null" }
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
                    val publicKeyBin = characteristic.value.copyOf(
                        SERIALIZED_PUBLIC_KEY_SIZE)
                    val peer = Peer(
                        bluetoothAddress = BluetoothAddress(gatt.device.address),
                        key = defaultCryptoProvider.keyFromPublicBin(publicKeyBin)
                    )
                    logger.debug { "adding verified peer: " + peer.mid }
                    network.addVerifiedPeer(peer)
                }
            }
        }

        override fun onCharacteristicWrite(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            logger.debug { "onCharacteristicWrite $status" }
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            logger.debug { "onCharacteristicChanged: ${characteristic.value}" }
        }
    }

    override fun isOpen(): Boolean {
        return isOpen
    }

    override fun send(address: BluetoothAddress, data: ByteArray) {
        val gatt = gatts[address]
        if (gatt != null) {
            val service = gatt.getService(IPv8GattServer.SERVICE_UUID)
            val characteristic = service
                .getCharacteristic(IPv8GattServer.CHARACTERISTIC_UUID)
            characteristic.value = data
            val success = gatt.writeCharacteristic(characteristic)
            logger.debug { "Write characteristic $address $success" }
        } else {
            logger.debug { "GATT for address not found: $address" }
        }
    }

    override fun open() {
        gattServer.open()
        gattServer.packetListener = this::notifyListeners
        bleAdvertiser.start()
        isOpen = true
    }

    override fun close() {
        bleAdvertiser.stop()
        gattServer.close()
        gattServer.packetListener = null
        isOpen = false
    }

    override fun connectTo(address: BluetoothAddress) {
        if (gatts[address] == null) {
            logger.debug { "Connect to $address" }
            val device = bluetoothManager.adapter.getRemoteDevice(address.mac)
            val gatt = device.connectGatt(context, false, gattCallback)
            gatts[address] = gatt
        } else {
            logger.warn { "Already connected to $address" }
        }
    }
}
