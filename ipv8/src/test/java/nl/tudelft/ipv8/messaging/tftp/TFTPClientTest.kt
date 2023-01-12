package nl.tudelft.ipv8.messaging.tftp

import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.apache.commons.net.DatagramSocketFactory
import org.apache.commons.net.tftp.*
import org.junit.Assert
import org.junit.Test
import java.io.ByteArrayInputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketTimeoutException

class TFTPClientTest {
    @Test
    fun sendFile_mockServer() {
        val socket = MockDatagramSocket()

        val client = TFTPClient()

        val payload = "Lorem Ipsum ".repeat(50).toByteArray(Charsets.US_ASCII)
        val input = ByteArrayInputStream(payload)
        val serverHost = InetAddress.getByName("192.168.0.12")
        val serverPort = 8090
        val clientHost = InetAddress.getByName("192.168.0.13")
        val clientPort = 8091

        val sentPackets = mutableListOf<TFTPPacket>()

        socket.onSend = { datagram ->
            val tftpPacket = TFTPPacket.newTFTPPacket(datagram)
            sentPackets += tftpPacket

            when (tftpPacket.type) {
                TFTPPacket.WRITE_REQUEST -> {
                    val ack = TFTPAckPacket(clientHost, clientPort, 0)
                    ack.address = serverHost
                    ack.port = serverPort
                    socket.receiveBuffer.trySend(ack.newDatagram()).isSuccess
                }
                TFTPPacket.DATA -> {
                    val dataPacket = tftpPacket as TFTPDataPacket
                    val ack = TFTPAckPacket(clientHost, clientPort, dataPacket.blockNumber)
                    ack.address = serverHost
                    ack.port = serverPort
                    socket.receiveBuffer.trySend(ack.newDatagram()).isSuccess
                }
                else -> {
                    throw IllegalStateException("Unsupported packet type: ${tftpPacket.type}")
                }
            }
        }

        client.setDatagramSocketFactory(object : DatagramSocketFactory {
            override fun createDatagramSocket(): DatagramSocket {
                return socket
            }

            override fun createDatagramSocket(port: Int): DatagramSocket {
                throw IllegalStateException("Operation not supported")
            }

            override fun createDatagramSocket(
                port: Int,
                laddr: InetAddress?
            ): DatagramSocket {
                throw IllegalStateException("Operation not supported")
            }
        })
        client.open()

        client.sendFile("filename", TFTP.BINARY_MODE, input, serverHost, serverPort)

        client.close()

        Assert.assertEquals(3, sentPackets.size)
        Assert.assertEquals(TFTPPacket.WRITE_REQUEST, sentPackets[0].type)
        Assert.assertEquals(TFTPPacket.DATA, sentPackets[1].type)
        Assert.assertEquals(TFTPPacket.DATA, sentPackets[2].type)
    }

    @Test
    fun sendFile_realServer() {
        val payload = "Lorem Ipsum ".repeat(50).toByteArray(Charsets.US_ASCII)
        val input = ByteArrayInputStream(payload)
        val serverHost = InetAddress.getByName("192.168.0.12")
        val serverPort = 8090
        val clientHost = InetAddress.getByName("192.168.0.13")
        val clientPort = 8091

        val socket = MockDatagramSocket()

        val client = TFTPClient()
        val server = TFTPServer { packet ->
            val datagram = packet.newDatagram()
            datagram.address = serverHost
            datagram.port = serverPort
            socket.receiveBuffer.trySend(datagram).isSuccess
        }

        val sentPackets = mutableListOf<TFTPPacket>()

        socket.onSend = { datagram ->
            val tftpPacket = TFTPPacket.newTFTPPacket(datagram)
            tftpPacket.address = clientHost
            tftpPacket.port = clientPort
            sentPackets += tftpPacket
            server.onPacket(tftpPacket)
        }

        client.setDatagramSocketFactory(object : DatagramSocketFactory {
            override fun createDatagramSocket(): DatagramSocket {
                return socket
            }

            override fun createDatagramSocket(port: Int): DatagramSocket {
                throw IllegalStateException("Operation not supported")
            }

            override fun createDatagramSocket(
                port: Int,
                laddr: InetAddress?
            ): DatagramSocket {
                throw IllegalStateException("Operation not supported")
            }
        })
        client.open()

        client.sendFile("filename", TFTP.BINARY_MODE, input, serverHost, serverPort)

        client.close()

        Assert.assertEquals(3, sentPackets.size)
        Assert.assertEquals(TFTPPacket.WRITE_REQUEST, sentPackets[0].type)
        Assert.assertEquals(TFTPPacket.DATA, sentPackets[1].type)
        Assert.assertEquals(TFTPPacket.DATA, sentPackets[2].type)
    }

    class MockDatagramSocket : DatagramSocket() {
        var onSend: ((DatagramPacket) -> Unit)? = null
        val receiveBuffer = Channel<DatagramPacket>(Channel.UNLIMITED)

        override fun send(packet: DatagramPacket) {
            onSend?.invoke(packet)
        }

        override fun receive(packet: DatagramPacket) {
            runBlocking {
                try {
                    withTimeout(1000) {
                        val received = receiveBuffer.receive()
                        packet.address = received.address
                        packet.port = received.port
                        packet.setData(received.data, received.offset, received.length)
                    }
                } catch (e: TimeoutCancellationException) {
                    throw SocketTimeoutException()
                }
            }
        }
    }
}
