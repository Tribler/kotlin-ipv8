package nl.tudelft.ipv8.messaging.utp.channels.impl

import mu.KotlinLogging
import nl.tudelft.ipv8.messaging.utp.UTPEndpoint
import nl.tudelft.ipv8.messaging.utp.data.UtpPacketUtils
import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket

private val logger2 = KotlinLogging.logger("UTPSocketBinder")

class UTPSocketBinder {
    companion object {
        @JvmStatic
        @Throws(IOException::class)
        fun send(socket: DatagramSocket, packet: DatagramPacket) {
            val utpPacket = UtpPacketUtils.extractUtpPacket(packet)
            logger2.debug {
                "Send packet to " + "${packet.address.hostName}:${packet.port}, seq=${utpPacket.sequenceNumber}, ack=${utpPacket.ackNumber}"
            }
            val wrappedData = byteArrayOf(UTPEndpoint.PREFIX_UTP) + packet.data
            packet.data = wrappedData
            socket.send(packet)
        }
    }
}
