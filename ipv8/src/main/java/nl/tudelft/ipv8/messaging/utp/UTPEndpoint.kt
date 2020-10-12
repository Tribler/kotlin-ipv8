package nl.tudelft.ipv8.messaging.utp

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import mu.KotlinLogging
import nl.tudelft.ipv8.IPv4Address
import nl.tudelft.ipv8.messaging.Endpoint
import nl.tudelft.ipv8.messaging.Packet
import nl.tudelft.ipv8.messaging.utp.channels.UtpSocketChannel
import nl.tudelft.ipv8.messaging.utp.channels.UtpSocketState
import nl.tudelft.ipv8.messaging.utp.channels.futures.UtpReadFuture
import nl.tudelft.ipv8.messaging.utp.channels.impl.receive.ConnectionIdTriplet
import nl.tudelft.ipv8.messaging.utp.data.UtpPacketUtils
import nl.tudelft.ipv8.messaging.utp.data.logger
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.util.*
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream


private val logger = KotlinLogging.logger {}

class UTPEndpoint : Endpoint<IPv4Address>() {
    var socket: DatagramSocket? = null

    private val connectionIds: MutableMap<Short, ConnectionIdTriplet> = HashMap()

    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.IO + job)

    private var busySending = false

    override fun send(peer: IPv4Address, data: ByteArray) {
        if (!busySending) {
            busySending = true
            logger.debug { "Sending with UTP to ${peer.ip}:${peer.port}" }
            scope.launch(Dispatchers.IO) {
                var compressedData: ByteArray? = null
                ByteArrayOutputStream().use { os ->
                    GZIPOutputStream(os).use { os2 ->
                        os2.write(data)
                    }
                    compressedData = os.toByteArray()
                }
                logger.debug { "Opening channel" }
                val channel = UtpSocketChannel.open(socket!!)
                logger.debug { "Connecting to channel" }
                val cFuture = channel.connect(InetSocketAddress(peer.ip, peer.port))
                registerChannel(channel)
                logger.debug { "Blocking" }
                cFuture.block()
                logger.debug { "Writing" }
                val fut = channel.write(ByteBuffer.wrap(compressedData!!))
                logger.debug { "Blocking again" }
                fut.block()
                logger.debug { "Closing channel" }
                channel.close()
                logger.debug { "Done" }
                busySending = false
            }
        } else {
            logger.warn { "Not sending UTP packet because still busy sending..." }
            return
        }
    }

    fun onPacket(packet: DatagramPacket) {
        val unwrappedData = packet.data.copyOfRange(1, packet.length)
        packet.data = unwrappedData
        val utpPacket = UtpPacketUtils.extractUtpPacket(packet)
        logger.debug("Received UTP packet from ${packet.address.hostAddress}:${packet.port}, seq=" + utpPacket.sequenceNumber + ", ack=" + utpPacket.ackNumber)

        if (UtpPacketUtils.isSynPkt(packet)) {
            logger.debug { "syn received" }
            synReceived(packet)
        } else {
            connectionIds[utpPacket.connectionId]!!.channel!!.receivePacket(packet)
        }
    }

    private fun synReceived(packet: DatagramPacket?) {
        if (handleDoubleSyn(packet)) {
            return
        }
        if (packet != null) {
            val channel = UtpSocketChannel.open(socket)
            channel.receivePacket(packet)
            registerChannel(channel)

            val readFuture: UtpReadFuture = channel.read { data: ByteArray ->
                val sourceAddress = IPv4Address(packet.address.hostAddress, packet.port)
                logger.debug("Received UTP file (${data.size} B) from $sourceAddress")
                logger.debug { "Data ${data.size}" }
                var uncompressedData: ByteArray? = null
                ByteArrayInputStream(data).use { stream ->
                    GZIPInputStream(stream).use { stream2 ->
                        uncompressedData = stream2.readBytes()
                    }
                }
                notifyListeners(Packet(sourceAddress, uncompressedData!!))
            }
            scope.launch {
                logger.debug("Blocking readFuture")
                readFuture.block()
                logger.debug("Done blocking readFuture")
            }
        }
    }

    /*
	 * handles double syn....
	 */
    private fun handleDoubleSyn(packet: DatagramPacket?): Boolean {
        val pkt = UtpPacketUtils.extractUtpPacket(packet)
        var connId = pkt.connectionId
        connId = (connId + 1).toShort()
        val triplet: ConnectionIdTriplet? = connectionIds[connId]
        if (triplet != null) {
            triplet.channel.receivePacket(packet)
            return true
        }
        return false
    }

    private fun registerChannel(channel: UtpSocketChannel): Boolean {
        val triplet =
            ConnectionIdTriplet(channel, channel.connectionIdReceiving, channel.connectionIdSending)
        if (isChannelRegistrationNecessary(channel)) {
            connectionIds[channel.connectionIdReceiving] = triplet
            return true
        }

        /* Connection id collision found or not been able to ack.
		 *  ignore this syn packet */
        return false
    }

    /*
	 * true if channel reg. is required.
	 */
    private fun isChannelRegistrationNecessary(channel: UtpSocketChannel): Boolean {
        return (connectionIds[channel.connectionIdReceiving] == null
            && channel.state != UtpSocketState.SYN_ACKING_FAILED)
    }

    override fun isOpen(): Boolean {
        TODO("Not yet implemented")
    }

    override fun open() {
    }

    override fun close() {
        TODO("Not yet implemented")
    }

    companion object {
        const val PREFIX_UTP: Byte = 67
    }
}
