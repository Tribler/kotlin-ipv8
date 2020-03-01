package nl.tudelft.ipv8.android.messaging.bluetooth

import android.bluetooth.*
import android.content.Context
import kotlinx.coroutines.*
import mu.KotlinLogging
import nl.tudelft.ipv8.Peer
import nl.tudelft.ipv8.messaging.Packet
import nl.tudelft.ipv8.messaging.bluetooth.BluetoothAddress
import nl.tudelft.ipv8.messaging.bluetooth.BluetoothEndpoint
import nl.tudelft.ipv8.peerdiscovery.Network
import no.nordicsemi.android.ble.BleServerManagerCallbacks

private val logger = KotlinLogging.logger {}

class BluetoothLeEndpoint(
    private val context: Context,
    private val bluetoothManager: BluetoothManager,
    private val gattServer: GattServerManager,
    private val bleAdvertiser: IPv8BluetoothLeAdvertiser,
    private val network: Network
) : BluetoothEndpoint() {
    /**
     * True if we are currently advertising and sending packets to connected GATT servers.
     */
    private var isOpen = false

    private val clients: MutableMap<BluetoothAddress, GattClientManager> = mutableMapOf()

    private val clientCallbacks = object : GattClientCallbacks() {
        override fun onPeerDiscovered(peer: Peer) {
            network.addVerifiedPeer(peer)
        }

        override fun onPacketWrite(device: BluetoothDevice, data: ByteArray) {
            notifyListeners(Packet(BluetoothAddress(device.address), data))
        }

        override fun onDeviceDisconnected(device: BluetoothDevice) {
            network.removeByAddress(BluetoothAddress(device.address))
            clients.remove(BluetoothAddress(device.address))
        }

        override fun onError(device: BluetoothDevice, message: String, errorCode: Int) {
            logger.error { "GATT error: $device $message $errorCode" }
        }
    }

    private val serverCallbacks = object : BleServerManagerCallbacks {
        override fun onDeviceConnectedToServer(device: BluetoothDevice) {
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

    override fun send(address: BluetoothAddress, data: ByteArray) {
        logger.debug { "Send to $address: ${data.size} B" }
        val client = clients[address]
        client?.send(data)
    }

    override fun open() {
        bleAdvertiser.start()
        gattServer.open()
        isOpen = true
    }

    override fun close() {
        bleAdvertiser.stop()
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
                .timeout(60_000L)
                .retry(3, 100)
                .done {
                    logger.info { "Device $it initiated" }
                }
                .enqueue()
            clients[address] = newManager
        } else {
            logger.warn { "Already connected to $address" }
        }
    }
}
