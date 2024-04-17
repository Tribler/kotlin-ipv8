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
import java.net.InetSocketAddress
import java.net.SocketAddress
import kotlin.time.Duration

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

    @Test
    fun fileReceivingTest() = runTest(timeout = Duration.parse("10s")){
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

        val senderSocketAddress = InetSocketAddress("127.0.0.2", 8090)
        val payload = "Hello World".toByteArray(Charsets.US_ASCII)
        val connectionIdSend: Short = 43
        val connectionIdReceive: Short = 42

        // send syn packet to endpoint
        val synPacket = UtpPacket()
        synPacket.sequenceNumber = 1
        synPacket.timestampDifference = 0
        synPacket.timestamp = 0
        synPacket.connectionId = connectionIdReceive
        synPacket.typeVersion = UtpPacketUtils.SYN
        synPacket.windowSize = 60000

        endpoint.onPacket(createDatagramPacket(synPacket, senderSocketAddress))

        // wait until ack packet is sent
        runBlocking { while(datagramsSent.isEmpty()){ delay(1)} }
        val ackPacket = extractUtpPacket(datagramsSent.get(0));

        // verify this packet acknowledges start of connection with id 42
        Assert.assertEquals(connectionIdReceive, ackPacket.connectionId)
        Assert.assertEquals(1.toShort(), ackPacket.ackNumber)


        // send data packet containing entire payload to endpoint
        val dataPacket = UtpPacket()
        dataPacket.sequenceNumber = 2
        dataPacket.ackNumber = 1
        dataPacket.timestampDifference = 10
        dataPacket.timestamp = 100
        dataPacket.connectionId = connectionIdSend
        dataPacket.typeVersion = UtpPacketUtils.DATA
        // only data packet, so set windowsize to 0 to indicate this is final packet
        dataPacket.windowSize = 0
        dataPacket.payload = payload

        // Utp uses selective acks, so we need to send datapacket twice before ack is produced
        // This can be considered a bug in utp4j, as we should probably directly acknowledge
        // the final data packet
        endpoint.onPacket(createDatagramPacket(dataPacket, senderSocketAddress))
        endpoint.onPacket(createDatagramPacket(dataPacket, senderSocketAddress))

        // wait until ack packet is sent
        runBlocking { while(datagramsSent.size == 1){ delay(1)} }
        val ackPacket2 = extractUtpPacket(datagramsSent.get(1));

        // verify this packet acknowledges the previous data packet
        Assert.assertEquals(connectionIdReceive, ackPacket2.connectionId)
        Assert.assertEquals(2.toShort(), ackPacket2.ackNumber)
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
