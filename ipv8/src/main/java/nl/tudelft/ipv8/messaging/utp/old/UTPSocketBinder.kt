package nl.tudelft.ipv8.messaging.utp.old

import mu.KotlinLogging
import nl.tudelft.ipv8.messaging.utp.UTPEndpoint
import java.net.DatagramSocket

private val logger = KotlinLogging.logger {}

class UTPSocketBinder {
    companion object {
        @JvmStatic
        fun send(socket: DatagramSocket, utpPacket: UTPPacket) {
            logger.debug {
                "Send UTP packet of type ${utpPacket.type} to " +
                    "${utpPacket.address.hostName}:${utpPacket.port}"
            }
            val packet = utpPacket.udpPacket
            val wrappedData = byteArrayOf(UTPEndpoint.PREFIX_UTP) + packet.data
            logger.debug { "wrappedData = ${wrappedData.joinToString(", ")}" }
            packet.setData(wrappedData, 0, packet.length + 1)
            socket.send(packet)
        }
    }
}
