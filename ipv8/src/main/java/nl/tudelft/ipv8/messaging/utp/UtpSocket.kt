package nl.tudelft.ipv8.messaging.utp

import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import nl.tudelft.ipv8.messaging.utp.UtpIPv8Endpoint.Companion.MAX_UTP_PACKET_SIZE
import nl.tudelft.ipv8.messaging.utp.UtpIPv8Endpoint.Companion.PREFIX_UTP
import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.SocketTimeoutException

class UtpSocket(private val socket: DatagramSocket?) : DatagramSocket() {
    val buffer = Channel<DatagramPacket>(Channel.UNLIMITED)

    override fun send(packet: DatagramPacket) {

        val data = packet.data.copyOfRange(packet.offset, packet.offset + packet.length)
        val wrappedData = byteArrayOf(PREFIX_UTP) + data
        packet.setData(wrappedData, 0, wrappedData.size)

        if (socket != null) {
            socket.send(packet)
            println("Sending $packet")
        } else {
            println("UTP socket is missing")
        }

    }

    override fun receive(packet: DatagramPacket) {
        runBlocking {
            try {
                withTimeout(5000) {
                    val received = buffer.receive()
                    packet.address = received.address
                    packet.port = received.port
                    packet.setData(received.data, received.offset, received.length)
                }
            } catch (e: TimeoutCancellationException) {
                // Is this just spamming the console if channel is empty?
                throw IOException()
//                println("socket timed out")
            }
        }
    }
}
