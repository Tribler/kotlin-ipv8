package nl.tudelft.ipv8.utp

import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.spyk
import io.mockk.verify
import nl.tudelft.ipv8.BaseCommunityTest
import nl.tudelft.ipv8.IPv4Address
import nl.tudelft.ipv8.Peer
import nl.tudelft.ipv8.exception.PacketDecodingException
import nl.tudelft.ipv8.keyvault.defaultCryptoProvider
import nl.tudelft.ipv8.messaging.Packet
import nl.tudelft.ipv8.messaging.payload.TransferRequestPayload
import nl.tudelft.ipv8.messaging.payload.TransferRequestPayload.TransferStatus.ACCEPT
import nl.tudelft.ipv8.messaging.payload.TransferRequestPayload.TransferStatus.REQUEST
import nl.tudelft.ipv8.messaging.payload.TransferRequestPayload.TransferType.FILE
import nl.tudelft.ipv8.messaging.payload.UtpHeartbeatPayload
import nl.tudelft.ipv8.messaging.utp.UtpCommunity
import nl.tudelft.ipv8.messaging.utp.UtpCommunity.MessageId.UTP_HEARTBEAT
import nl.tudelft.ipv8.messaging.utp.UtpCommunity.MessageId.UTP_TRANSFER_REQUEST
import nl.tudelft.ipv8.messaging.utp.UtpIPv8Endpoint
import nl.tudelft.ipv8.peerdiscovery.Network
import nl.tudelft.ipv8.util.hexToBytes
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class UtpCommunityTest : BaseCommunityTest() {

    private val peer1: Peer = Peer(defaultCryptoProvider.generateKey(), IPv4Address("5.2.3.4", 5234))
    private val peer2: Peer = Peer(defaultCryptoProvider.generateKey(), IPv4Address("1.2.3.4", 2342))

    private fun getCommunity(): UtpCommunity {
        val myPrivateKey = getPrivateKey()
        val myPeer = Peer(myPrivateKey)
        val endpoint = getEndpoint()
        val network = Network()
        val community = UtpCommunity()
        community.myPeer = myPeer
        community.endpoint = endpoint
        community.network = network
        return community
    }

    @Before
    fun setup() {
        mockkObject(UtpIPv8Endpoint.Companion)
        every { UtpIPv8Endpoint.Companion.getBufferSize() } returns 10_000
    }

    @Test
    fun sendHeartbeatTest() {
        val community = spyk(getCommunity(), recordPrivateCalls = true)
        every { community.getPeers() } returns listOf(peer1, peer2)

        community.sendHeartbeat()

        verify {
            community.serializePacket(
                UTP_HEARTBEAT,
                any<UtpHeartbeatPayload>(),
                sign = false,
                peer = any(),
                prefix = any()
            )
        }
        verify(exactly = 1) { community.getPeers() }
        // I can't verify the send method because it's protected (using this as a workaround)
        verify(exactly = 2) { community.endpoint }
    }

    @Test
    fun heartbeatUpdatesLastHeartbeatForPeer() {
        val community = spyk(getCommunity(), recordPrivateCalls = true)
        every { community.getPeers() } returns listOf(peer1, peer2)


        val payload = "0002450ded7389134595dadb6b2549f431ad60156931010000000000000001"
        val packet = Packet(peer1.address, payload.hexToBytes())
        community.onHeartbeat(packet)
        assert(community.lastHeartbeat.containsKey(peer1.mid))
    }

    @Test
    fun heartbeatDoesNotUpdateLastHeartbeatForUnknownPeer() {
        val community = spyk(getCommunity(), recordPrivateCalls = true)

        val payload = "0002450ded7389134595dadb6b2549f431ad60156931010000000000000001"
        val unknownPeer = Peer(defaultCryptoProvider.generateKey(), IPv4Address("1.1.1.1", 1111))
        val packet = Packet(unknownPeer.address, payload.hexToBytes())
        community.onHeartbeat(packet)
        assert(!community.lastHeartbeat.containsKey(unknownPeer.mid))
    }

    @Test
    fun onHeartbeatPacketTest() {
        val community = spyk(getCommunity(), recordPrivateCalls = true)

        val payload = "0002450ded7389134595dadb6b2549f431ad60156931010000000000000001"
        val handler = mockk<(Packet) -> Unit>(relaxed = true)
        community.messageHandlers[UTP_HEARTBEAT] = handler

        community.onPacket(Packet(peer1.address, payload.hexToBytes()))
        verify { handler(any()) }
    }

    @Test
    fun sendTransferRequestTest() {
        val community = spyk(getCommunity(), recordPrivateCalls = true)

        community.sendTransferRequest(peer1, "test.txt", 100, FILE)

        verify {
            community.serializePacket(
                UTP_TRANSFER_REQUEST,
                any<TransferRequestPayload>(),
                sign = true,
                peer = any(),
                prefix = any()
            )
        }
        // I can't verify the send method because it's protected (using this as a workaround)
        verify(exactly = 1) { community.endpoint }
    }


    @Test
    fun sendTransferRequestResponseTest() {
        val community = spyk(getCommunity(), recordPrivateCalls = true)

        val payload = TransferRequestPayload("test.txt", REQUEST, FILE, 100)
        community.sendTransferResponse(peer1, payload)

        verify {
            community.serializePacket(
                UTP_TRANSFER_REQUEST,
                payload,
                sign = true,
                peer = any(),
                prefix = any()
            )
        }
        // I can't verify the send method because it's protected (using this as a workaround)
        verify(exactly = 1) { community.endpoint }
    }

    @Test
    fun onTransferRequestPacketTest() {
        val community = spyk(getCommunity(), recordPrivateCalls = true)

        val payload = "0002450ded7389134595dadb6b2549f431ad60156931020000000000000001"
        val handler = mockk<(Packet) -> Unit>(relaxed = true)
        community.messageHandlers[UTP_TRANSFER_REQUEST] = handler

        community.onPacket(Packet(peer1.address, payload.hexToBytes()))
        verify { handler(any()) }
    }

    @Test
    fun transferRequestUpdatesTransferRequestsForPeer() {
        val community = spyk(getCommunity(), recordPrivateCalls = true)

        val packet = mockk<Packet>()
        val payload = TransferRequestPayload("test.txt", REQUEST, FILE, 100)

        every { packet.source } returns peer1.address
        every { packet.getAuthPayload(TransferRequestPayload.Deserializer) } returns (peer1 to payload)

        community.onTransferRequest(packet)
        verify { community.sendTransferResponse(any(), payload.copy(status = ACCEPT)) }
        assert(community.transferRequests.containsKey(peer1.mid))
    }

    @Test
    fun transferRequestDoesNotUpdateTransferRequestsForUnknownPeer() {
        val community = spyk(getCommunity(), recordPrivateCalls = true)

        val packet = mockk<Packet>()

        every { packet.source } returns peer2.address
        every { packet.getAuthPayload(TransferRequestPayload.Deserializer) } throws PacketDecodingException("Invalid signature!")

        community.onTransferRequest(packet)
        verify(exactly = 0) { community.sendTransferResponse(any(), any()) }
        assert(!community.transferRequests.containsKey(peer2.mid))
    }

}
