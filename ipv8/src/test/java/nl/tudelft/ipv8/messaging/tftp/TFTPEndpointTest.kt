package nl.tudelft.ipv8.messaging.tftp

import io.mockk.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runBlockingTest
import nl.tudelft.ipv8.IPv4Address
import org.apache.commons.net.tftp.TFTP
import org.apache.commons.net.tftp.TFTPAckPacket
import org.apache.commons.net.tftp.TFTPWriteRequestPacket
import org.junit.Assert
import org.junit.Test
import java.io.InputStream
import java.net.DatagramSocket
import java.net.InetAddress

@OptIn(ExperimentalCoroutinesApi::class)
class TFTPEndpointTest {
    @Suppress("DEPRECATION") // TODO: rewrite usage of coroutines in testing.
    @Test
    fun send() = runBlockingTest {
        val tftpClient = spyk<TFTPClient>()
        val socket = mockk<DatagramSocket>(relaxed = true)
        val tftpEndpoint = TFTPEndpoint(tftpClient)
        val host = "192.168.0.1"
        val port = 8080
        val address = IPv4Address(host, port)
        val data = "Hello world".toByteArray(Charsets.US_ASCII)
        tftpEndpoint.socket = socket
        tftpEndpoint.open()
        tftpEndpoint.send(address, data)

        // TODO: fix flaky test
        /*
        verify {
            val inputMatcher = match<InputStream> {
                it.readBytes().contentEquals(data)
            }
            tftpClient.sendFile(any(), any(), inputMatcher, InetAddress.getByName(host), port)
        }
        */
    }

    @Suppress("DEPRECATION") // TODO: rewrite usage of coroutines in testing.
    /* @Test */
    fun onPacket_forServer() = runBlockingTest {
        val tftpClient = mockk<TFTPClient>(relaxed = true)
        val socket = mockk<DatagramSocket>(relaxed = true)
        val tftpEndpoint = TFTPEndpoint(tftpClient)
        val tftpServer = spyk(tftpEndpoint.tftpServer)
        tftpEndpoint.tftpServer = tftpServer
        val host = "192.168.0.1"
        val port = 8080
        tftpEndpoint.socket = socket
        tftpEndpoint.open()

        val writeRequest = TFTPWriteRequestPacket(InetAddress.getByName(host), port,
            "filename", TFTP.BINARY_MODE)
        val datagram = writeRequest.newDatagram()
        datagram.setData(byteArrayOf() + TFTPEndpoint.PREFIX_TFTP + datagram.data,
            0, datagram.length + 1)
        tftpEndpoint.onPacket(datagram)

        verify {
            tftpServer.onPacket(match {
                it.toString() == writeRequest.toString()
            })

            socket.send(match {
                it.data[0] == TFTPEndpoint.PREFIX_TFTP
            })
        }
    }

    @Suppress("DEPRECATION") // TODO: rewrite usage of coroutines in testing.
    @Test
    fun onPacket_forClient() = runBlockingTest {
        val tftpClient = TFTPClient()
        val socket = mockk<DatagramSocket>(relaxed = true)
        val tftpEndpoint = TFTPEndpoint(tftpClient)
        val host = "192.168.0.1"
        val port = 8080
        tftpEndpoint.socket = socket
        tftpEndpoint.open()

        val ack = TFTPAckPacket(InetAddress.getByName(host), port, 0)
        val datagram = ack.newDatagram()
        datagram.setData(byteArrayOf() + TFTPEndpoint.PREFIX_TFTP + datagram.data,
            0, datagram.length + 1)
        tftpEndpoint.onPacket(datagram)

        val received = tftpClient.receive()
        Assert.assertEquals(ack.toString(), received.toString())
    }
}
