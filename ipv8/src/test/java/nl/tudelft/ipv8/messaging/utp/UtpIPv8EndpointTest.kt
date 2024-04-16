package nl.tudelft.ipv8.messaging.utp

import org.junit.Assert
import org.junit.Test
import io.mockk.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runBlockingTest
import kotlinx.coroutines.test.runTest
import nl.tudelft.ipv8.IPv4Address
import nl.tudelft.ipv8.Peer
import nl.tudelft.ipv8.messaging.utp.UtpIPv8Endpoint.Companion.PREFIX_UTP
import java.net.DatagramPacket
import java.net.DatagramSocket
import kotlin.experimental.and
import kotlin.experimental.or

class UtpIPv8EndpointTest {
    @Test
    fun connectionTest() = runTest{
        val endpoint = UtpIPv8Endpoint()
        val socket = mockk<DatagramSocket>(relaxed = true)
        endpoint.udpSocket = socket
        endpoint.open()

        val datagramSlot = slot<DatagramPacket>()
        every {
            socket.send(capture(datagramSlot))
        } answers {
            Unit
        }

        val peer = IPv4Address("127.0.0.1", 8090)
        val payload = "payload".toByteArray(Charsets.US_ASCII)

        // start sending file
        endpoint.send(peer, payload)
        runBlocking { delay(10) }

        // first packet to be sent should be SYN packet
        verify {
            // verify first packet was sent
            socket.send(any())

            // verify it has the correct prefix for UTP
            val datagramPacket = datagramSlot.captured
            val prefix = datagramPacket.data[0]

            Assert.assertEquals(prefix, PREFIX_UTP)
        }
    }
}
