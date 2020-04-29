package nl.tudelft.ipv8.messaging

import nl.tudelft.ipv8.IPv4Address
import nl.tudelft.ipv8.Peer
import nl.tudelft.ipv8.messaging.bluetooth.BluetoothAddress
import nl.tudelft.ipv8.messaging.bluetooth.BluetoothEndpoint
import nl.tudelft.ipv8.messaging.udp.UdpEndpoint
import java.util.*

/**
 * A link aggregator that abstracts away multiple endpoints supported by a peer.
 */
class EndpointAggregator(
    val udpEndpoint: UdpEndpoint?,
    val bluetoothEndpoint: BluetoothEndpoint?
) : Endpoint<Peer>(), EndpointListener {
    private var isOpen: Boolean = false

    /**
     * Sends a message to the peer. Currently it sends over all available endpoints. In the future,
     * the method should send only over the most suitable transport.
     */
    override fun send(peer: Peer, data: ByteArray) {
        peer.lastRequest = Date()

        if (!peer.address.isEmpty() && udpEndpoint != null) {
            udpEndpoint.send(peer, data)
        }

        val bluetoothAddress = peer.bluetoothAddress
        if (bluetoothAddress != null && bluetoothEndpoint != null) {
            bluetoothEndpoint.send(bluetoothAddress, data)
        }
    }

    /**
     * Sends a packet to the specified address.
     */
    fun send(address: Address, data: ByteArray) {
        when (address) {
            is IPv4Address -> udpEndpoint?.send(address, data)
            is BluetoothAddress -> bluetoothEndpoint?.send(address, data)
        }
    }

    fun connectTo(address: BluetoothAddress) {
        bluetoothEndpoint?.connectTo(address)
    }

    override fun isOpen(): Boolean {
        return isOpen
    }

    override fun open() {
        udpEndpoint?.addListener(this)
        udpEndpoint?.open()

        bluetoothEndpoint?.addListener(this)
        bluetoothEndpoint?.open()

        isOpen = true
    }

    override fun close() {
        udpEndpoint?.removeListener(this)
        udpEndpoint?.close()

        bluetoothEndpoint?.removeListener(this)
        bluetoothEndpoint?.close()

        isOpen = false
    }

    override fun onPacket(packet: Packet) {
        notifyListeners(packet)
    }

    override fun onEstimatedLanChanged(address: IPv4Address) {
        setEstimatedLan(address)
    }
}
