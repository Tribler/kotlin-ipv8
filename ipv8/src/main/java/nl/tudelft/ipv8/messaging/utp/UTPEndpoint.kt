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
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.util.*


private val logger = KotlinLogging.logger {}

class UTPEndpoint : Endpoint<IPv4Address>() {
    //    var utpSocket: UTPSocket? = null
    var socket: DatagramSocket? = null

    private val connectionIds: MutableMap<Int, ConnectionIdTriplet> = HashMap()

    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.IO + job)

    private var channel: UtpSocketChannel? = null

    override fun send(peer: IPv4Address, data: ByteArray) {
        if (channel == null) {
            logger.debug { "Sending with UTP to ${peer.ip}:${peer.port}" }
            scope.launch(Dispatchers.IO) {
                logger.debug { "Opening channel" }
                channel = UtpSocketChannel.open(socket!!)
                logger.debug { "Connecting to channel" }
                val cFuture = channel!!.connect(InetSocketAddress(peer.ip, peer.port))
                logger.debug { "Blocking" }
                cFuture.block()
                logger.debug { "Writing" }
                val fut = channel!!.write(ByteBuffer.wrap(data))
                logger.debug { "Blocking again" }
                fut.block()
                logger.debug { "Closing channel" }
                channel!!.close()
                channel = null
                logger.debug { "Done" }
            }
        } else {
            logger.warn { "Not sending UTP packet because still busy..." }
            return
        }
    }

    fun onPacket(packet: DatagramPacket) {
        //logger.debug { "Data ${packet.data.size} = ${packet.data.joinToString("\n")}" }

        // Unwrap prefix
        val unwrappedData = packet.data.copyOfRange(1, packet.length)
        packet.data = unwrappedData

        /*val sb = StringBuilder(packet.data.joinToString(", "))
        if (sb.length > 950) {
            logger.debug { "sb.length = " + sb.length }
            val chunkCount: Int = sb.length / 950 // integer division
            for (i in 0..chunkCount) {
                val max = 950 * (i + 1)
                if (max >= sb.length) {
                    logger.debug { "chunk " + i + " of " + chunkCount + ":" + sb.substring(950 * i) }
                } else {
                    logger.debug { "chunk " + i + " of " + chunkCount + ":" + sb.substring(950 * i, max) }
                }
            }
        } else {
            logger.debug { sb.toString() }
        }*/

        if (UtpPacketUtils.isSynPkt(packet)) {
            logger.debug { "syn received" }
            synReceived(packet)
        } else {
            channel!!.receivePacket(packet)
//            val utpPacket = UtpPacketUtils.extractUtpPacket(packet)
//            val triplet = connectionIds.get(utpPacket.connectionId)
//            triplet?.channel?.receivePacket(packet)
        }


        //If the packet is at least 20 bytes in length (the minimum UTP header size)
        //We assume it's a UTP Packet and try to establish a connection
        /*if (packet.length >= 20) {
            val utpPacket = UTPPacket(packet)
            logger.debug {
                "Received UTP packet of type ${utpPacket.type} (${packet.length} B) " +
                    "from ${packet.address.hostAddress}:${packet.port}"
            }
            when (utpPacket.type) {
                UTPSocket.ST_SYN.toInt() -> {
                    scope.launch {
                        val connectionIDSendBytes: ByteArray = utpPacket.connectionID
                        val connectionIDReceiveBytes = ByteArray(2)
                        val temp = HelperClass.intToBytes(
                            HelperClass.bytesToInt(
                                connectionIDSendBytes
                            ) + 1
                        )
                        connectionIDReceiveBytes[0] = temp[2]
                        connectionIDReceiveBytes[1] = temp[3]
                        val secnumber = 0
                        //Now we try to confirm the connection, this will involve sending a connection confirm packet
                        //and waiting for an ACK (similar to TCP's 3-way handshake). If we are unsuccessful,
                        //we'll loop back and wait for incoming packets again
                        //Now we try to confirm the connection, this will involve sending a connection confirm packet
                        //and waiting for an ACK (similar to TCP's 3-way handshake). If we are unsuccessful,
                        //we'll loop back and wait for incoming packets again
                        try {
                            utpSocket =
                                UTPSocket(
                                    socket,
                                    utpPacket.address,
                                    utpPacket.port,
                                    connectionIDSendBytes,
                                    connectionIDReceiveBytes,
                                    secnumber
                                )
                            utpSocket!!.initializeReceiver()
                        } catch (e: SocketTimeoutException) {
                            e.printStackTrace()
                        }
                    }
                }
                else -> {
                    utpSocket!!.packetBuffer.put(utpPacket)
                    System.out.println("asdf")
                }
            }
        }*/
    }


    /*
	 * handles syn packet.
	 */
    private fun synReceived(packet: DatagramPacket?) {
//        if (handleDoubleSyn(packet)) { DIT MOET WSS WEL AAAAAAAAAAAAAANSTTTTTTTTTTTTAAAAAAAAAAANNNNNN!!!!!!!1
//            return
//        }
        if (packet != null) {
            channel = UtpSocketChannel.open(socket)
            channel!!.receivePacket(packet)
            registerChannel(channel!!)

            val readFuture: UtpReadFuture = channel!!.read { data: ByteArray ->
                val sourceAddress = IPv4Address(packet.address.hostAddress, packet.port)
                logger.debug("Received UTP file (${data.size} B) from $sourceAddress")
                logger.debug { "Data ${data.size}" }
                notifyListeners(Packet(sourceAddress, data))
            }
            scope.launch {
                logger.debug("Blocking readFuture")
                readFuture.block()
                logger.debug("Done blocking readFuture")
            }

            /* Collision in Connection ids or failed to ack.
			 * Ignore Syn Packet and let other side handle the issue. */
//            if (!registered) {
//                utpChannel = null
//            }
        }
    }

    /*
	 * handles double syn....
	 */
    private fun handleDoubleSyn(packet: DatagramPacket?): Boolean {
        val pkt = UtpPacketUtils.extractUtpPacket(packet)
        var connId = pkt.connectionId
        connId = (connId and 0xFFFF) + 1
        val triplet: ConnectionIdTriplet? = connectionIds.get(connId)
        if (triplet != null) {
            triplet.channel.receivePacket(packet)
            return true
        }
        return false
    }

    /*
	 * registers a channel.
	 */
    private fun registerChannel(channel: UtpSocketChannel): Boolean {
        val triplet = ConnectionIdTriplet(
            channel, channel.connectionIdReceiving, channel.connectionIdsending
        )
        if (isChannelRegistrationNecessary(channel)) {
            connectionIds.put((channel.connectionIdReceiving and 0xFFFF), triplet)
            return true
        }

        /* Connection id collision found or not been able to ack.
		 *  ignore this syn packet */return false
    }

    /*
	 * true if channel reg. is required.
	 */
    private fun isChannelRegistrationNecessary(channel: UtpSocketChannel): Boolean {
        return (connectionIds.get(channel.connectionIdReceiving) == null
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
