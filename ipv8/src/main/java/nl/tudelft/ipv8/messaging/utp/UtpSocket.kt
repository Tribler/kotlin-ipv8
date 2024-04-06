package nl.tudelft.ipv8.messaging.utp

import nl.tudelft.ipv8.messaging.utp.UtpIPv8Endpoint.Companion.MAX_UTP_PACKET_SIZE
import nl.tudelft.ipv8.messaging.utp.UtpIPv8Endpoint.Companion.PREFIX_UTP
import java.net.DatagramPacket
import java.net.DatagramSocket

class UtpSocket(private val socket: DatagramSocket?) : DatagramSocket() {

    override fun send(packet: DatagramPacket) {

        val data = packet.data.copyOfRange(packet.offset, packet.offset + packet.length)
        val wrappedData = byteArrayOf(PREFIX_UTP) + data
        packet.setData(wrappedData, 0, wrappedData.size)

        if (socket != null) {
            socket.send(packet)
        } else {
            println("UTP socket is missing")
        }

    }

    override fun receive(p: DatagramPacket) {
        val packet = DatagramPacket(ByteArray(MAX_UTP_PACKET_SIZE), MAX_UTP_PACKET_SIZE)
        if (socket != null) {
            socket.receive(packet)
            val data = packet.data.copyOfRange(1, packet.length)

            p.address = packet.address
            p.port = packet.port
            p.setData(data, 0, data.size)

        } else {
            println("UTP socket is missing")
        }
    }
}
