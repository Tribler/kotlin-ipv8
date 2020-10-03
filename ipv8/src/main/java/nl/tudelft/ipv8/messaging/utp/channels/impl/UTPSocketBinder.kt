package nl.tudelft.ipv8.messaging.utp.channels.impl

import mu.KotlinLogging
import nl.tudelft.ipv8.messaging.utp.UTPEndpoint
import java.net.DatagramPacket
import java.net.DatagramSocket

private val logger2 = KotlinLogging.logger("UTPSocketBinder")

class UTPSocketBinder {
    companion object {
        @JvmStatic
        fun send(socket: DatagramSocket, packet: DatagramPacket) {
            logger2.debug {
                "Send packet to " + "${packet.address.hostName}:${packet.port}"
            }

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

            val wrappedData = byteArrayOf(UTPEndpoint.PREFIX_UTP) + packet.data
            packet.data = wrappedData
            socket.send(packet)
        }
    }
}
