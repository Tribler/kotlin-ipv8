package nl.tudelft.ipv8.utp

import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.verify
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import nl.tudelft.ipv8.IPv4Address
import nl.tudelft.ipv8.Peer
import nl.tudelft.ipv8.messaging.payload.TransferRequestPayload
import nl.tudelft.ipv8.messaging.payload.TransferRequestPayload.TransferStatus.ACCEPT
import nl.tudelft.ipv8.messaging.payload.TransferRequestPayload.TransferStatus.DECLINE
import nl.tudelft.ipv8.messaging.payload.TransferRequestPayload.TransferType.FILE
import nl.tudelft.ipv8.messaging.payload.TransferRequestPayload.TransferType.RANDOM_DATA
import nl.tudelft.ipv8.messaging.utp.UtpCommunity
import nl.tudelft.ipv8.messaging.utp.UtpHelper
import nl.tudelft.ipv8.messaging.utp.UtpIPv8Endpoint
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Before
import org.junit.Test
import java.security.MessageDigest
import kotlin.time.Duration.Companion.seconds

class UtpHelperTest {

    private val community = mockk<UtpCommunity>(relaxed = true)
    private val utpHelper = UtpHelper(community)

    @Before
    fun setup() {
        mockkObject(UtpIPv8Endpoint)
        every { UtpIPv8Endpoint.Companion.getBufferSize() } returns 10_000
        every { community.sendHeartbeat() } returns Unit
    }

    @Test
    fun testHeartbeatCoroutineOpen() {
        every { community.endpoint.udpEndpoint?.utpIPv8Endpoint?.isOpen() } returns true

        runBlocking {
            utpHelper.startHeartbeat()
            delay(6000)
            utpHelper.stopHeartbeat()
        }

        verify(exactly = 2) { community.sendHeartbeat() }
    }

    @Test
    fun testHeartbeatCoroutineClosed() {
        every { community.endpoint.udpEndpoint?.utpIPv8Endpoint?.isOpen() } returns false

        utpHelper.startHeartbeat()

        verify(exactly = 0) { community.sendHeartbeat() }
    }

    @Test
    fun testSendFileData() {
        val peer = mockk<Peer>()

        every { peer.mid } returns "test_mid"
        every { peer.address } returns IPv4Address("1.1.1.1",1111)
        every { community.transferRequests } returns mutableMapOf(("test_mid" to TransferRequestPayload("test", ACCEPT, FILE, 10)))

        val metadata = UtpHelper.NamedResource("test", 10)
        val data = ByteArray(10)

        utpHelper.sendFileData(peer, metadata, data)

        verify(exactly = 1) { community.sendTransferRequest(peer, metadata.name, metadata.size, FILE) }
        verify(exactly = 2) { community.transferRequests }
        verify(exactly = 1) { community.endpoint.udpEndpoint?.sendUtp(any(), any()) }
    }

    @Test
    fun testSendFileDataNoResponse() {
        val peer = mockk<Peer>()

        every { peer.mid } returns "test_mid"
        every { community.transferRequests } returns mutableMapOf()

        val metadata = UtpHelper.NamedResource("test", 10)
        val data = ByteArray(10)

        utpHelper.sendFileData(peer, metadata, data)

        verify(exactly = 1) { community.sendTransferRequest(peer, metadata.name, metadata.size, FILE) }
        verify(exactly = 0) { community.endpoint.udpEndpoint?.sendUtp(any(), any()) }
    }

    @Test
    fun testSendFileDataDecline() {
        val peer = mockk<Peer>()

        every { peer.mid } returns "test_mid"
        every { community.transferRequests } returns mutableMapOf(("test_mid" to TransferRequestPayload("test", DECLINE, FILE, 10)))

        val metadata = UtpHelper.NamedResource("test", 10)
        val data = ByteArray(10)

        utpHelper.sendFileData(peer, metadata, data)

        verify(exactly = 1) { community.sendTransferRequest(peer, metadata.name, metadata.size, FILE) }
        verify(exactly = 2) { community.transferRequests }
        verify(exactly = 0) { community.endpoint.udpEndpoint?.sendUtp(any(), any()) }
    }

    @Test
    fun testSendRandomData() {
        val peer = mockk<Peer>()

        every { peer.mid } returns "test_mid"
        every { peer.address } returns IPv4Address("1.1.1.1",1111)
        every { community.transferRequests } returns mutableMapOf(("test_mid" to TransferRequestPayload("test", ACCEPT, FILE, 10)))

        val size = 5_000
        val metadata = UtpHelper.NamedResource("random.tmp", 0, size)

        utpHelper.sendRandomData(peer, size)

        verify(exactly = 1) { community.sendTransferRequest(peer, metadata.name, metadata.size, RANDOM_DATA) }
        verify(exactly = 2) { community.transferRequests }
        verify(exactly = 1) { community.endpoint.udpEndpoint?.sendUtp(any(), any()) }
    }

    @Test
    fun testSendRandomDataDefault() {
        val peer = mockk<Peer>()

        every { peer.mid } returns "test_mid"
        every { peer.address } returns IPv4Address("1.1.1.1",1111)
        every { community.transferRequests } returns mutableMapOf(("test_mid" to TransferRequestPayload("test", ACCEPT, FILE, 10)))

        val metadata = UtpHelper.NamedResource("random.tmp", 0, UtpIPv8Endpoint.getBufferSize())

        utpHelper.sendRandomData(peer)

        verify(exactly = 1) { community.sendTransferRequest(peer, metadata.name, metadata.size, RANDOM_DATA) }
        verify(exactly = 2) { community.transferRequests }
        verify(exactly = 1) { community.endpoint.udpEndpoint?.sendUtp(any(), any()) }
    }

    @Test
    fun testSendRandomDataNoResponse() {
        val peer = mockk<Peer>()

        every { peer.mid } returns "test_mid"
        every { community.transferRequests } returns mutableMapOf()

        val size = 5_000
        val metadata = UtpHelper.NamedResource("random.tmp", 0, size)

        utpHelper.sendRandomData(peer, size)

        verify(exactly = 1) { community.sendTransferRequest(peer, metadata.name, metadata.size, RANDOM_DATA) }
        verify(exactly = 0) { community.endpoint.udpEndpoint?.sendUtp(any(), any()) }
    }

    @Test
    fun testSendRandomDataDecline() {
        val peer = mockk<Peer>()

        every { peer.mid } returns "test_mid"
        every { community.transferRequests } returns mutableMapOf(("test_mid" to TransferRequestPayload("test", DECLINE, FILE, 10)))

        val size = 5_000
        val metadata = UtpHelper.NamedResource("random.tmp", 0, size)

        utpHelper.sendRandomData(peer, size)

        verify(exactly = 1) { community.sendTransferRequest(peer, metadata.name, metadata.size, RANDOM_DATA) }
        verify(exactly = 2) { community.transferRequests }
        verify(exactly = 0) { community.endpoint.udpEndpoint?.sendUtp(any(), any()) }
    }

    @Test
    fun generateRandomDataBufferReturnsCorrectSize() {
        val size = 1000
        val data = UtpHelper.generateRandomDataBuffer(size)
        assertEquals(size, data.size)
    }

    @Test
    fun generateRandomDataBufferReturnsUniqueData() {
        val data1 = UtpHelper.generateRandomDataBuffer()
        val data2 = UtpHelper.generateRandomDataBuffer()
        assertNotEquals(data1, data2)
    }

    @Test
    fun generateRandomDataBufferContainsSHA256Hash() {
        val size = 1000
        val data = UtpHelper.generateRandomDataBuffer(size)
        val messageDigest = MessageDigest.getInstance("SHA-256")
        val hash = messageDigest.digest(data.copyInto(ByteArray(size), 0, 0, size - 32))
        val dataHash = data.copyOfRange(size - 32, size)
        assertArrayEquals(hash, dataHash)
    }

    @Test(expected = IllegalArgumentException::class)
    fun generateRandomDataBufferThrowsExceptionForSizeLessThan32() {
        UtpHelper.generateRandomDataBuffer(31)
    }

    @Test(expected = IllegalArgumentException::class)
    fun generateRandomDataBufferThrowsExceptionForSizeGreaterThanBufferSize() {
        UtpHelper.generateRandomDataBuffer(UtpIPv8Endpoint.getBufferSize() + 1)
    }

    @Test
    fun namedResourceToString() {
        val namedResource = UtpHelper.NamedResource("test", 10)
        assertEquals("test", namedResource.toString())
    }

}
