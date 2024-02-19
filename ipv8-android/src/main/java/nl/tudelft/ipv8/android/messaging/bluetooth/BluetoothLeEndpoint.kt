package nl.tudelft.ipv8.android.messaging.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.*
import android.content.Context
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import mu.KotlinLogging
import nl.tudelft.ipv8.Peer
import nl.tudelft.ipv8.messaging.Packet
import nl.tudelft.ipv8.messaging.bluetooth.BluetoothAddress
import nl.tudelft.ipv8.messaging.bluetooth.BluetoothEndpoint
import nl.tudelft.ipv8.peerdiscovery.Network
import no.nordicsemi.android.ble.BleServerManagerCallbacks

private val logger = KotlinLogging.logger {}

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class BluetoothLeEndpoint(
    private val context: Context,
    private val bluetoothManager: BluetoothManager,
    private val gattServer: GattServerManager,
    private val bleAdvertiser: IPv8BluetoothLeAdvertiser,
    private val bleScanner: IPv8BluetoothLeScanner,
    private val network: Network,
    private val myPeer: Peer,
) : BluetoothEndpoint() {
    /**
     * True if we are currently advertising and sending packets to connected GATT servers.
     */
    private var isOpen = false

    private val clients: MutableMap<BluetoothAddress, GattClientManager> = mutableMapOf()

    private val clientCallbacks =
        object : GattClientCallbacks() {
            override fun onPeerDiscovered(peer: Peer) {
                network.addVerifiedPeer(peer)
            }

            override fun onPacketWrite(
                device: BluetoothDevice,
                data: ByteArray,
            ) {
                notifyListeners(Packet(BluetoothAddress(device.address), data))
            }

            override fun onDeviceDisconnected(device: BluetoothDevice) {
                network.removeByAddress(BluetoothAddress(device.address))
                clients.remove(BluetoothAddress(device.address))
            }

            override fun onError(
                device: BluetoothDevice,
                message: String,
                errorCode: Int,
            ) {
                logger.error { "GATT error: $device $message $errorCode" }
            }
        }

    private val serverCallbacks =
        object : BleServerManagerCallbacks {
            override fun onDeviceConnectedToServer(device: BluetoothDevice) {
                // A device has connected to our GATT server. Initiate connection to their GATT server to provide
                // bidirectional communication.
                logger.info { "Device connected to server: $device" }
                connectTo(BluetoothAddress(device.address))
            }

            override fun onDeviceDisconnectedFromServer(device: BluetoothDevice) {
                logger.info { "Device disconnected from server: $device" }
            }

            override fun onServerReady() {
                // NOOP
            }
        }

    init {
        gattServer.setManagerCallbacks(serverCallbacks)
    }

    override fun isOpen(): Boolean {
        return isOpen
    }

    override fun send(
        peer: BluetoothAddress,
        data: ByteArray,
    ) {
        logger.debug { "Send to $peer: ${data.size} B" }
        val client = clients[peer]
        client?.send(data)
    }

    override fun open() {
        ensureBluetoothEnabled()
        gattServer.open()
        bleAdvertiser.start(myPeer)
        bleScanner.startPeriodicScan(SCAN_DURATION, SCAN_PAUSE)
        isOpen = true
    }

    /**
     * Enable Bluetooth if not enabled.
     */
    @SuppressLint("MissingPermission")
    private fun ensureBluetoothEnabled() {
        val bluetoothAdapter = bluetoothManager.adapter
        if (!bluetoothAdapter.isEnabled) {
            @Suppress("DEPRECATION") // TODO: fix bluetooth permission handling.
            bluetoothAdapter.enable()
        }
    }

    override fun close() {
        bleAdvertiser.stop()
        bleScanner.stopPeriodicScan()
        gattServer.close()
        isOpen = false

        // Disconnect from all GATT servers
        for (manager in clients.values) {
            manager.disconnect()
        }
        clients.clear()
    }

    override fun connectTo(address: BluetoothAddress) {
        val manager = clients[address]
        if (manager == null) {
            logger.debug { "Connect to $address" }
            val device = bluetoothManager.adapter.getRemoteDevice(address.mac)

            val newManager = GattClientManager(context)
            newManager.useServer(gattServer)
            newManager.setManagerCallbacks(clientCallbacks)
            newManager.connect(device)
                .timeout(CONNECTION_TIMEOUT)
                .retry(CONNECTION_RETRY_COUNT, CONNECTION_RETRY_DELAY)
                .done {
                    logger.info { "Device $it initiated" }
                }
                .enqueue()
            clients[address] = newManager
        } else {
            logger.warn { "Already connected to $address" }
        }
    }

    companion object {
        private const val SCAN_DURATION = 10_000L
        private const val SCAN_PAUSE = 5_000L
        private const val CONNECTION_TIMEOUT = 60_000L
        private const val CONNECTION_RETRY_COUNT = 3
        private const val CONNECTION_RETRY_DELAY = 100
    }
}
