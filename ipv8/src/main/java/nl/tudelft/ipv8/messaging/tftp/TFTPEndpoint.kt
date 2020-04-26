package nl.tudelft.ipv8.messaging.tftp

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import mu.KotlinLogging
import nl.tudelft.ipv8.IPv4Address
import nl.tudelft.ipv8.messaging.Endpoint
import nl.tudelft.ipv8.messaging.Packet
import org.apache.commons.net.DatagramSocketFactory
import org.apache.commons.net.tftp.*
import java.io.ByteArrayInputStream
import java.net.*

private val logger = KotlinLogging.logger {}

class TFTPEndpoint : Endpoint<IPv4Address>() {
    private val tftpClient = MyTFTPClient()
    private val tftpSocket = TFTPSocket()
    private val tftpServer = TFTPServer(tftpClient)

    var socket: DatagramSocket? = null

    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.IO + job)

    init {
        tftpServer.onFileReceived = { data, address, port ->
            val sourceAddress =
                IPv4Address(address.hostAddress, port)
            val packet = Packet(sourceAddress, data)
            logger.debug(
                "Received TFTP file (${data.size} B) from $sourceAddress"
            )
            notifyListeners(packet)
        }
    }

    override fun isOpen(): Boolean {
        return socket?.isBound == true
    }

    override fun send(address: IPv4Address, data: ByteArray) {
        scope.launch {
            val inputStream = ByteArrayInputStream(data)
            val inetAddress = Inet4Address.getByName(address.ip)

            withContext(Dispatchers.IO) {
                try {
                    tftpClient.sendFile(
                        TFTP_FILENAME,
                        TFTP.BINARY_MODE,
                        inputStream,
                        inetAddress,
                        address.port
                    )
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    suspend fun onPacket(packet: DatagramPacket) {
        val tftpPacket = TFTPPacket.newTFTPPacket(packet)

        logger.debug { "Received TFTP packet of type ${tftpPacket.type} (${packet.length} B) from ${packet.address.hostAddress}:${packet.port}" }

        if (tftpPacket is TFTPWriteRequestPacket || tftpPacket is TFTPDataPacket) {
            // This is a packet for the server
            tftpServer.onPacket(tftpPacket)
        } else if (tftpPacket is TFTPAckPacket || tftpPacket is TFTPErrorPacket) {
            // This is a packet for the client
            tftpSocket.buffer.offer(packet)
        } else {
            // This is an unsupported packet (ReadRequest)
            logger.debug { "Unsupported TFTP packet type" }
        }
    }

    override fun open() {
        tftpClient.setDatagramSocketFactory(object : DatagramSocketFactory {
            override fun createDatagramSocket(): DatagramSocket {
                return tftpSocket
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
        tftpClient.open()
    }

    override fun close() {
        tftpClient.close()
    }

    /**
     * A socket that serves as a proxy between the actual DatagramSocket and TFTP implementation.
     */
    inner class TFTPSocket : DatagramSocket() {
        val buffer = Channel<DatagramPacket>(Channel.UNLIMITED)

        override fun send(packet: DatagramPacket) {
            val tftpPacket = TFTPPacket.newTFTPPacket(packet)
            logger.debug { "Send TFTP packet of type ${tftpPacket.type} to ${packet.address.hostName}:${packet.port} (${packet.length} B)" }
            val data = packet.data.copyOfRange(packet.offset, packet.offset + packet.length)
            val wrappedData = byteArrayOf(PREFIX_TFTP) + data
            val wrappedPacket = DatagramPacket(
                wrappedData,
                wrappedData.size,
                packet.address,
                packet.port
            )

            val socket = socket
            if (socket != null) {
                socket.send(wrappedPacket)
            } else {
                logger.error { "TFTP socket is missing" }
            }
        }

        override fun receive(packet: DatagramPacket) {
            runBlocking {
                try {
                    withTimeout(1000) {
                        val received = buffer.receive()
                        packet.address = received.address
                        packet.port = received.port
                        packet.setData(received.data, received.offset, received.length)
                        val tftpPacket = TFTPPacket.newTFTPPacket(packet)
                        logger.debug { "Client received TFTP packet of type ${tftpPacket.type} from ${received.address.hostName} (${packet.length} B)" }
                    }
                } catch (e: TimeoutCancellationException) {
                    throw SocketTimeoutException()
                }
            }
        }
    }

    companion object {
        private const val TFTP_FILENAME = "ipv8_packet.bin"
        const val PREFIX_TFTP: Byte = 69
    }
}
