package nl.tudelft.ipv8.messaging.utp

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import nl.tudelft.ipv8.IPv4Address
import nl.tudelft.ipv8.messaging.Endpoint
import java.io.OutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.SocketTimeoutException
import java.nio.ByteBuffer


class UTPEndpoint : Endpoint<IPv4Address>() {
    var utpSocket: UTPSocket? = null
    var socket: DatagramSocket? = null

    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.IO + job)

    override fun send(peer: IPv4Address, data: ByteArray) {
        scope.launch(Dispatchers.IO) {
            val conn = UTPSocket(socket!!, Inet4Address.getByName(peer.ip), peer.port)
            val out: OutputStream = conn.outputStream
            out.write(data)
            out.close()
        }
    }

    fun onPacket(packet: DatagramPacket) {
        // Unwrap prefix
        val unwrappedData = packet.data.copyOfRange(1, packet.length)
        packet.setData(unwrappedData, 0, unwrappedData.size)

        //If the packet is at least 20 bytes in length (the minimum UTP header size)
        //We assume it's a UTP Packet and try to establish a connection
        if (packet.length >= 20) {
            val utpPacket = UTPPacket(packet)
            when (utpPacket.type) {
                UTPSocket.ST_SYN.toInt() -> {
                    scope.launch {
                        val connectionIDSendBytes: ByteArray = utpPacket.connectionID
                        val connectionIDReceiveBytes = ByteArray(2)
                        val temp = intToBytes(bytesToInt(connectionIDSendBytes) + 1)
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
                            utpSocket = UTPSocket(
                                socket,
                                utpPacket.address,
                                utpPacket.port,
                                connectionIDSendBytes,
                                connectionIDReceiveBytes,
                                secnumber
                            )
                        } catch (e: SocketTimeoutException) {
                        }
                    }
                }
                else -> utpSocket!!.packetBuffer.offer(utpPacket)
            }
        }
    }

    private fun bytesToInt(bytes: ByteArray): Int {
        return ByteBuffer.wrap(bytes).int
    }

    private fun intToBytes(int: Int): ByteArray {
        return ByteBuffer.allocate(4).putInt(int).array()
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
