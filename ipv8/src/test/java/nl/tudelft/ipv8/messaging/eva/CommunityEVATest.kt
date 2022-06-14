package nl.tudelft.ipv8.messaging.eva

import io.mockk.mockk
import io.mockk.spyk
import io.mockk.verify
import nl.tudelft.ipv8.*
import nl.tudelft.ipv8.messaging.Packet
import nl.tudelft.ipv8.peerdiscovery.Network
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import java.math.BigDecimal
import java.math.RoundingMode

class CommunityEVATest : BaseCommunityTest() {
    private fun getCommunity(): TestCommunity {
        val myPrivateKey = getPrivateKey()
        val myPeer = Peer(myPrivateKey)
        val endpoint = getEndpoint()
        val network = Network()

        val community = TestCommunity()
        community.myPeer = myPeer
        community.endpoint = endpoint
        community.network = network
        community.evaProtocolEnabled = true

        return community
    }

    @Test
    fun onEVAWriteRequestPacket() {
        val myPrivateKey = getPrivateKey()
        val myPeer = Peer(myPrivateKey)

        val community = getCommunity()
        val handler = mockk<(Packet) -> Unit>(relaxed = true)
        community.messageHandlers[Community.MessageId.EVA_WRITE_REQUEST] = handler

        val info = Community.EVAId.EVA_PEERCHAT_ATTACHMENT
        val id = "0123456789abcdefghijklmnopqrstuvw"
        val nonce = (0..EVAProtocol.MAX_NONCE).random().toULong()
        val dataString = "Lorem ipsum dolor sit amet"
        val data = dataString.toByteArray(Charsets.UTF_8)
        val dataSize = data.size.toULong()
        val blockSize = 10
        val blockCount = BigDecimal(data.size).divide(BigDecimal(blockSize), RoundingMode.UP).toInt().toUInt()

        community.createEVAWriteRequest(
            myPeer,
            info,
            id,
            nonce,
            dataSize,
            blockCount,
            EVAProtocol.BLOCK_SIZE.toUInt(),
            EVAProtocol.WINDOW_SIZE.toUInt()
        ).let { packet ->
            community.onPacket(Packet(myPeer.address, packet))
        }

        verify { handler(any()) }
    }

    @Test
    fun handleEVAWriteRequest() {
        val myPrivateKey = getPrivateKey()
        val myPeer = Peer(myPrivateKey)
        val community = spyk(getCommunity())

        community.createEVAWriteRequest(
            myPeer,
            Community.EVAId.EVA_PEERCHAT_ATTACHMENT,
            "0123456789",
            1234.toULong(),
            10000.toULong(),
            10.toUInt(),
            EVAProtocol.BLOCK_SIZE.toUInt(),
            EVAProtocol.WINDOW_SIZE.toUInt()
        ).let { packet ->
            community.onEVAWriteRequestPacket(Packet(myPeer.address, packet))
        }

        verify { community.onEVAWriteRequest(any(), any()) }
    }

    @Test
    fun onEVAAcknowledgementPacket() {
        val myPrivateKey = getPrivateKey()
        val myPeer = Peer(myPrivateKey)

        val community = getCommunity()
        val handler = mockk<(Packet) -> Unit>(relaxed = true)
        community.messageHandlers[Community.MessageId.EVA_ACKNOWLEDGEMENT] = handler

        community.createEVAAcknowledgement(
            myPeer,
            nonce = (0..EVAProtocol.MAX_NONCE).random().toULong(),
            ackWindow = 2.toUInt(),
            unReceivedBlocks = listOf(1,2,3).encodeToByteArray()
        ).let { packet ->
            community.onPacket(Packet(myPeer.address, packet))
        }

        verify { handler(any()) }
    }

    @Test
    fun handleEVAAcknowledgement() {
        val myPrivateKey = getPrivateKey()
        val myPeer = Peer(myPrivateKey)
        val community = spyk(getCommunity())

        community.createEVAAcknowledgement(
            myPeer,
            nonce = (0..EVAProtocol.MAX_NONCE).random().toULong(),
            ackWindow = 2.toUInt(),
            unReceivedBlocks = listOf(1,2,3).encodeToByteArray()
        ).let { packet ->
            community.onEVAAcknowledgementPacket(Packet(myPeer.address, packet))
        }

        verify { community.onEVAAcknowledgement(any(), any()) }
    }

    @Test
    fun onEVADataPacket() {
        val myPrivateKey = getPrivateKey()
        val myPeer = Peer(myPrivateKey)

        val community = getCommunity()
        val handler = mockk<(Packet) -> Unit>(relaxed = true)
        community.messageHandlers[Community.MessageId.EVA_DATA] = handler

        val blockNumber = 2
        val nonce = (0..EVAProtocol.MAX_NONCE).random().toULong()
        val dataString = "Lorem ipsum dolor sit amet"
        val blockSize = 5
        val data = dataString.toByteArray(Charsets.UTF_8).takeInRange(blockNumber*blockSize, (blockNumber+1)*blockSize - 1)

        community.createEVAData(
            myPeer,
            blockNumber.toUInt(),
            nonce,
            data
        ).let { packet ->
            community.onPacket(Packet(myPeer.address, packet))
        }

        verify { handler(any()) }
    }

    @Test
    fun handleEVAData() {
        val myPrivateKey = getPrivateKey()
        val myPeer = Peer(myPrivateKey)
        val community = spyk(getCommunity())

        val blockNumber = 2
        val nonce = (0..EVAProtocol.MAX_NONCE).random().toULong()
        val dataString = "Lorem ipsum dolor sit amet"
        val blockSize = 5
        val data = dataString.toByteArray(Charsets.UTF_8).takeInRange(blockNumber*blockSize, (blockNumber+1)*blockSize - 1)

        community.createEVAData(
            myPeer,
            blockNumber.toUInt(),
            nonce,
            data
        ).let { packet ->
            community.onEVADataPacket(Packet(myPeer.address, packet))
        }

        verify { community.onEVAData(any(), any()) }
    }

    @Test
    fun onEVAErrorPacket() {
        val myPrivateKey = getPrivateKey()
        val myPeer = Peer(myPrivateKey)

        val community = getCommunity()
        val handler = mockk<(Packet) -> Unit>(relaxed = true)
        community.messageHandlers[Community.MessageId.EVA_ERROR] = handler

        val info = Community.EVAId.EVA_PEERCHAT_ATTACHMENT
        val message = "Lorem ipsum dolor sit amet"

        community.createEVAError(
            myPeer,
            info,
            message
        ).let { packet ->
            community.onPacket(Packet(myPeer.address, packet))
        }

        verify { handler(any()) }
    }

    @Test
    fun handleEVAError() {
        val myPrivateKey = getPrivateKey()
        val myPeer = Peer(myPrivateKey)
        val community = spyk(getCommunity())

        community.createEVAError(
            myPeer,
            Community.EVAId.EVA_PEERCHAT_ATTACHMENT,
            "Lorem ipsum dolor sit amet"
        ).let { packet ->
            community.onEVAErrorPacket(Packet(myPeer.address, packet))
        }

        verify { community.onEVAError(any(), any()) }
    }

    @Test
    fun sendEVAWriteRequest() {
        val community = getCommunity()
        community.load()

        val previousRequest = community.myPeer.lastRequest

        community.createEVAWriteRequest(
            community.myPeer,
            Community.EVAId.EVA_PEERCHAT_ATTACHMENT,
            "01245678",
            (0..EVAProtocol.MAX_NONCE).random().toULong(),
            "lorem ipsum".toByteArray().size.toULong(),
            5.toUInt(),
            EVAProtocol.BLOCK_SIZE.toUInt(),
            EVAProtocol.WINDOW_SIZE.toUInt()
        ).let { packet ->
            community.endpoint.send(community.myPeer, packet)
        }

        val lastRequest = community.myPeer.lastRequest

        assertEquals(previousRequest, null)
        assertNotEquals(previousRequest, lastRequest)

        community.unload()
    }

    @Test
    fun sendEVAAcknowledgement() {
        val community = getCommunity()
        community.load()

        val previousRequest = community.myPeer.lastRequest

        community.createEVAAcknowledgement(
            community.myPeer,
            (0..EVAProtocol.MAX_NONCE).random().toULong(),
            2.toUInt(),
            listOf(10, 13).encodeToByteArray()
        ).let { packet ->
            community.endpoint.send(community.myPeer, packet)
        }

        val lastRequest = community.myPeer.lastRequest

        assertEquals(previousRequest, null)
        assertNotEquals(previousRequest, lastRequest)

        community.unload()
    }

    @Test
    fun sendEVAData() {
        val community = getCommunity()
        community.load()

        val previousRequest = community.myPeer.lastRequest

        community.createEVAData(
            community.myPeer,
            5.toUInt(),
            (0..EVAProtocol.MAX_NONCE).random().toULong(),
            "Lorem ipsum dolor sit amet".toByteArray(Charsets.UTF_8)
        ).let { packet ->
            community.endpoint.send(community.myPeer, packet)
        }

        val lastRequest = community.myPeer.lastRequest

        assertEquals(previousRequest, null)
        assertNotEquals(previousRequest, lastRequest)

        community.unload()
    }

    @Test
    fun sendEVAError() {
        val community = getCommunity()
        community.load()

        val previousRequest = community.myPeer.lastRequest

        community.createEVAError(
            community.myPeer,
            Community.EVAId.EVA_PEERCHAT_ATTACHMENT,
            "Error occurred"
        ).let { packet ->
            community.endpoint.send(community.myPeer, packet)
        }

        val lastRequest = community.myPeer.lastRequest

        assertEquals(null, previousRequest)
        assertNotEquals(previousRequest, lastRequest)

        community.unload()
    }

    @Test
    fun setOnEVASendCompleteCallback() {
        val community = getCommunity()
        community.load()

        assertEquals(null, community.evaProtocol?.onSendCompleteCallback)
        community.setOnEVASendCompleteCallback { _, _, _ -> }
        assertNotEquals(null, community.evaProtocol?.onSendCompleteCallback)

        community.unload()
    }

    @Test
    fun setOnEVAReceiveCompleteCallback() {
        val community = getCommunity()
        community.load()

        assertEquals(null, community.evaProtocol?.onReceiveCompleteCallback)
        community.setOnEVAReceiveCompleteCallback { _, _, _, _ -> }
        assertNotEquals(null, community.evaProtocol?.onReceiveCompleteCallback)

        community.unload()
    }

    @Test
    fun setOnEVAReceiveProgressCallback() {
        val community = getCommunity()
        community.load()

        assertEquals(null, community.evaProtocol?.onReceiveProgressCallback)
        community.setOnEVAReceiveProgressCallback { _, _, _ -> }
        assertNotEquals(null, community.evaProtocol?.onReceiveProgressCallback)

        community.unload()
    }

    @Test
    fun setOnEVAErrorCallback() {
        val community = getCommunity()
        community.load()

        assertEquals(null, community.evaProtocol?.onErrorCallback)
        community.setOnEVAErrorCallback { _, _ ->  }
        assertNotEquals(null, community.evaProtocol?.onErrorCallback)

        community.unload()
    }
}
