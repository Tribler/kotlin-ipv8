package nl.tudelft.ipv8.messaging.utp

import io.mockk.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import net.utp4j.data.UtpPacket
import net.utp4j.data.UtpPacketUtils
import net.utp4j.data.bytes.UnsignedTypesUtil
import nl.tudelft.ipv8.IPv4Address
import nl.tudelft.ipv8.messaging.utp.UtpIPv8Endpoint.Companion.PREFIX_UTP
import org.junit.Assert
import org.junit.Test
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketAddress
import java.util.Arrays

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
            // verify whether first packet was sent
            socket.send(any())

            // verify it has the correct prefix for UTP
            val prefixedPacket = datagramSlot.captured
            val prefix = prefixedPacket.data[0]

            Assert.assertEquals(prefix, PREFIX_UTP)

            // verify first packet was formatted correctly
            val packet = extractUtpPacket(prefixedPacket)
            Assert.assertTrue(UtpPacketUtils.isSynPkt(packet))
        }

        endpoint.close()
    }

    @Test
    fun fileSendingTest() = runTest{
        val endpoint = UtpIPv8Endpoint()
        val socket = mockk<DatagramSocket>(relaxed = true)
        endpoint.udpSocket = socket
        endpoint.open()

        // capture all sent datagrams
        val datagramsSent = mutableListOf<DatagramPacket>()
        every {
            socket.send(capture(datagramsSent))
        } answers {
            Unit
        }

        val peer = IPv4Address("127.0.0.1", 8090)
        val payload = "Hello World".toByteArray(Charsets.US_ASCII)

        // start sending file
        endpoint.send(peer, payload)

        // wait until SYN packet is sent
        runBlocking { while(datagramsSent.isEmpty()){ delay(1)} }
        val synPacket = extractUtpPacket(datagramsSent.get(0));

        // then send back ack to complete connection
        val ackPacket = createAckPacket(synPacket, 10, 40000, true)
        val datagramAckPacket = createDatagramPacket(ackPacket!!, datagramsSent.get(0).socketAddress)
        endpoint.onPacket(datagramAckPacket)

        // wait until first data packet is sent
        runBlocking { while(datagramsSent.size == 1){ delay(1)} }
        val dataPacket = extractUtpPacket(datagramsSent.get(1));
        println(synPacket)
        println(dataPacket)
        // verify that received packet is final packet
        Assert.assertTrue(dataPacket.windowSize == 0)
        // verify that payload is as expected
        Assert.assertArrayEquals(payload, dataPacket.payload)
    }

    private fun extractUtpPacket(prefixedPacket: DatagramPacket): UtpPacket {
        val packet = DatagramPacket(ByteArray(prefixedPacket.length - 1), prefixedPacket.length - 1)
        val data = prefixedPacket.data.copyOfRange(1, prefixedPacket.length)
        packet.setData(data, 0, data.size)
        packet.address = prefixedPacket.address
        packet.port = prefixedPacket.port

        return UtpPacketUtils.extractUtpPacket(packet)
    }

    private fun createAckPacket(
        pkt: UtpPacket, timedifference: Int,
        advertisedWindow: Long,
        syn: Boolean = false
    ): UtpPacket? {
        val ackPacket = UtpPacket()

        ackPacket.ackNumber = pkt.ackNumber
        ackPacket.timestampDifference = timedifference
        ackPacket.timestamp = pkt.timestamp + 10
        if (syn) {
            ackPacket.connectionId = (pkt.connectionId).toShort()
        } else {
            ackPacket.connectionId = (pkt.connectionId + 1).toShort()
        }

        ackPacket.typeVersion = UtpPacketUtils.STATE
        ackPacket.windowSize = UnsignedTypesUtil.longToUint(advertisedWindow)
        return ackPacket
    }

    private fun createDatagramPacket(packet: UtpPacket, address: SocketAddress): DatagramPacket {
        val utpPacketBytes: ByteArray = byteArrayOf(PREFIX_UTP) + packet.toByteArray()
        val length: Int = packet.getPacketLength() + 1
        return DatagramPacket(
            utpPacketBytes, length,
            address
        )
    }
}
