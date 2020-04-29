package nl.tudelft.ipv8.messaging.udp

import io.mockk.mockk
import io.mockk.verify
import nl.tudelft.ipv8.Community
import nl.tudelft.ipv8.IPv4Address
import nl.tudelft.ipv8.messaging.EndpointListener
import nl.tudelft.ipv8.messaging.tftp.TFTPEndpoint
import org.junit.Assert.*
import org.junit.Test
import java.net.DatagramPacket
import java.net.InetAddress

class UdpEndpointTest {
    @Test
    fun openAndClose() {
        val endpoint = UdpEndpoint(1234, InetAddress.getByName("0.0.0.0"))
        assertFalse(endpoint.isOpen())
        endpoint.open()
        assertTrue(endpoint.isOpen())
        endpoint.close()
        assertFalse(endpoint.isOpen())
    }

    @Test
    fun sendAndReceive() {
        val endpoint = UdpEndpoint(1234, InetAddress.getByName("0.0.0.0"))
        val listener = mockk<EndpointListener>(relaxed = true)
        endpoint.addListener(listener)

        endpoint.open()
        val data = "Hello world!".toByteArray(Charsets.US_ASCII)
        endpoint.send(IPv4Address("0.0.0.0", 1234), data)

        endpoint.close()
    }

    @Test
    fun handleReceivedPacket_udp() {
        val endpoint = UdpEndpoint(1234, InetAddress.getByName("0.0.0.0"))
        val listener = mockk<EndpointListener>(relaxed = true)
        endpoint.addListener(listener)

        endpoint.open()

        val data = byteArrayOf() + Community.PREFIX_IPV8 + Community.VERSION +
            "Lorem ipsum".toByteArray(Charsets.US_ASCII)
        val packet = DatagramPacket(data, 0, data.size)
        packet.address = InetAddress.getByName("192.168.0.1")

        endpoint.handleReceivedPacket(packet)

        verify {
            listener.onPacket(any())
        }

        endpoint.close()
    }

    @Test
    fun handleReceivedPacket_tftp() {
        val tftpEndpoint = mockk<TFTPEndpoint>(relaxed = true)
        val endpoint = UdpEndpoint(1234, InetAddress.getByName("0.0.0.0"), tftpEndpoint)
        val listener = mockk<EndpointListener>(relaxed = true)
        endpoint.addListener(listener)

        endpoint.open()

        val data = byteArrayOf() + TFTPEndpoint.PREFIX_TFTP +
            "Lorem ipsum".toByteArray(Charsets.US_ASCII)
        val packet = DatagramPacket(data, 0, data.size)
        packet.address = InetAddress.getByName("192.168.0.1")

        endpoint.handleReceivedPacket(packet)

        verify {
            tftpEndpoint.onPacket(packet)
        }

        endpoint.close()
    }
}
