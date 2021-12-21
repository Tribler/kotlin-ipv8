package nl.tudelft.ipv8.messaging.eva

import io.mockk.mockk
import kotlinx.coroutines.*
import nl.tudelft.ipv8.BaseCommunityTest
import nl.tudelft.ipv8.Community
import nl.tudelft.ipv8.Peer
import nl.tudelft.ipv8.TestCommunity
import nl.tudelft.ipv8.keyvault.Key
import nl.tudelft.ipv8.peerdiscovery.Network
import org.junit.Assert.*
import org.junit.Test
import java.util.*

class EVAProtocolTest : BaseCommunityTest() {
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
    fun startProtocol_true() {
        val community = getCommunity()

        community.evaProtocolEnabled = true
        community.load()
        assertEquals(community.evaProtocol != null, true)

        community.unload()
    }

    @Test
    fun startProtocol_false() {
        val community = getCommunity()

        community.evaProtocolEnabled = false
        community.load()
        assertEquals(community.evaProtocol != null, false)

        community.unload()
    }

    @Test
    fun scheduleTask() {
        val community = getCommunity()
        community.load()

        assertEquals(community.evaProtocol != null, true)

        community.evaProtocol?.let { evaProtocol ->
            @Suppress("UNCHECKED_CAST")
            val scheduledTasks = evaProtocol.getPrivateProperty("scheduledTasks") as PriorityQueue<ScheduledTask>

            assertEquals(0, scheduledTasks.size)

            evaProtocol.javaClass.declaredMethods.first {
                it.name == "scheduleTask"
            }.let { method ->
                method.isAccessible = true

                val atTime = Date().time + 100

                method.invoke(evaProtocol, atTime, {
                    2 + 2
                })

                assertEquals(1, scheduledTasks.size)
                val task = scheduledTasks.peek()
                assertEquals(atTime, task.atTime)
            }
        }

        community.unload()
    }

    @Test
    fun send() {
        val community = getCommunity()
        community.load()

        community.evaProtocol?.let { evaProtocol ->
            evaProtocol.javaClass.declaredMethods.first {
                it.name == "send"
            }.let { method ->
                method.isAccessible = true

                assertEquals(community.myPeer.lastRequest, null)

                val packet = community.createEVAWriteRequest(
                    Community.EVAId.EVA_PEERCHAT_ATTACHMENT,
                    "0123456789",
                    1234.toULong(),
                    100.toULong(),
                    10
                )

                method.invoke(evaProtocol, community.myPeer, packet)

                assertNotEquals(community.myPeer.lastRequest, null)
            }
        }

        community.unload()
    }

    @Test
    fun sendBinary_scheduled() {
        val community = getCommunity()
        community.load()

        val mockPeer = Peer(mockk())

        @Suppress("UNCHECKED_CAST")
        val scheduled = community.evaProtocol?.getPrivateProperty("scheduled") as MutableMap<Key, Queue<ScheduledTransfer>>
        assertEquals(0, scheduled.size)
        community.evaProtocol?.sendBinary(mockPeer, Community.EVAId.EVA_PEERCHAT_ATTACHMENT, "012345678", byteArrayOf(0, 1, 2, 3, 4, 5))
        assertEquals(1, scheduled.size)

        community.unload()
    }

    @Test
    fun startOutgoingTransfer_scheduled() {
        val community = getCommunity()
        community.load()

        val mockPeer = Peer(mockk())

        @Suppress("UNCHECKED_CAST")
        val scheduled = community.evaProtocol?.getPrivateProperty("scheduled") as MutableMap<Key, Queue<ScheduledTransfer>>

        community.evaProtocol?.let { evaProtocol ->
            evaProtocol.javaClass.declaredMethods.first {
                it.name == "startOutgoingTransfer"
            }.let { method ->
                method.isAccessible = true

                assertEquals(0, scheduled.size)
                method.invoke(
                    evaProtocol,
                    mockPeer,
                    Community.EVAId.EVA_PEERCHAT_ATTACHMENT,
                    "012345678",
                    byteArrayOf(0, 1, 2, 3, 4, 5),
                    1234
                )
                assertEquals(1, scheduled.size)
            }
        }

        community.unload()
    }

    @Test
    fun onWriteRequest_datasize_zero() {

        val community = getCommunity()
        community.load()

        var error = ""
        community.setOnEVAErrorCallback { _, exception ->
            error = exception.m
        }

        val payload = EVAWriteRequestPayload(
            Community.EVAId.EVA_PEERCHAT_ATTACHMENT,
            "0123456789",
            1234.toULong(),
            0.toULong(),
            10
        )

        val lastRequest = community.myPeer.lastRequest
        community.onEVAWriteRequest(community.myPeer, payload)

        // Error packet should be sent, meaning last request shouldn't be empty anymore
        val nextRequest = community.myPeer.lastRequest
        assertEquals(null, lastRequest)
        assertNotEquals(null, nextRequest)

        // Error callback should have been called
        assertEquals(error, "Data size can not be less or equal to 0")

        community.unload()
    }

    @Test
    fun onWriteRequest_datasize_over_limit() {

        val community = getCommunity()
        community.load()

        var error = ""
        community.setOnEVAErrorCallback { _, exception ->
            error = exception.m
        }

        val dataSize = EVAProtocol.BINARY_SIZE_LIMIT

        val payload = EVAWriteRequestPayload(
            Community.EVAId.EVA_PEERCHAT_ATTACHMENT,
            "0123456789",
            1234.toULong(),
            (dataSize + 1).toULong(),
            10
        )

        val lastRequest = community.myPeer.lastRequest
        community.onEVAWriteRequest(community.myPeer, payload)

        // Error packet should be sent, meaning last request shouldn't be empty anymore
        val nextRequest = community.myPeer.lastRequest
        assertEquals(null, lastRequest)
        assertNotEquals(null, nextRequest)

        // Error callback should have been called
        assertEquals(error, "Current data size limit($dataSize) has been exceeded")

        community.unload()
    }

    @Test
    fun onWriteRequest() {

        val community = getCommunity()
        community.load()

        val payload = EVAWriteRequestPayload(
            Community.EVAId.EVA_PEERCHAT_ATTACHMENT,
            "0123456789",
            1234.toULong(),
            10000.toULong(),
            10
        )

        @Suppress("UNCHECKED_CAST")
        val incoming = community.evaProtocol?.getPrivateProperty("incoming") as MutableMap<Key, Transfer>

        assertEquals(0, incoming.size)
        community.onEVAWriteRequest(community.myPeer, payload)
        assertEquals(1, incoming.size)

        community.unload()
    }

    @Test
    fun onAcknowledgement() {
        val community = getCommunity()
        community.load()

        val info = Community.EVAId.EVA_PEERCHAT_ATTACHMENT
        val id = "0123456789abcdefghijklmnopqrst"
        val nonce = (0..EVAProtocol.MAX_NONCE).random().toULong()
        val data = "Lorem ipsum dolor sit amet".toByteArray(Charsets.UTF_8)
        val blockCount = 6
        val blockSize = 5

        val payload = EVAAcknowledgementPayload(
            16,
            nonce,
            2,
            listOf(1, 2).encodeToByteArray()
        )

        val scheduledTransfer = ScheduledTransfer(info, data, nonce, id, blockCount, data.size.toULong(), blockSize)
        val transfer = Transfer(TransferType.OUTGOING, scheduledTransfer)
        transfer.ackedWindow = 1

        val outgoingMap: MutableMap<Key, Transfer> = mutableMapOf(Pair(community.myPeer.key, transfer))
        @Suppress("UNCHECKED_CAST")
        val outgoing = community.evaProtocol?.setAndReturnPrivateProperty("outgoing", outgoingMap) as MutableMap<Key, Transfer>
        assertEquals(1, outgoing.size)

        // Confirm that data packets are transmitted
        assertEquals(null, community.myPeer.lastRequest)
        assertEquals(0, outgoing[community.myPeer.key]?.lastSentBlocks?.size)

        community.onEVAAcknowledgement(community.myPeer, payload)
        assertNotEquals(null, community.myPeer.lastRequest)
        assertNotEquals(0, outgoing[community.myPeer.key]?.lastSentBlocks?.size)

        community.unload()
    }

    @Test
    fun transmitData() {
        val community = getCommunity()
        community.load()

        val info = Community.EVAId.EVA_PEERCHAT_ATTACHMENT
        val id = "0123456789abcdefghijklmnopqrst"
        val nonce = (0..EVAProtocol.MAX_NONCE).random().toULong()
        val data = "Lorem ipsum dolor sit amet".toByteArray(Charsets.UTF_8)
        val blockCount = 6
        val blockSize = 5

        val scheduledTransfer = ScheduledTransfer(info, data, nonce, id, blockCount, data.size.toULong(), blockSize)
        val transfer = Transfer(TransferType.OUTGOING, scheduledTransfer)
        transfer.windowSize = 5
        transfer.ackedWindow = 1
        transfer.setSendSet()

//        val outgoingMap: MutableMap<Key, Transfer> = mutableMapOf(Pair(community.myPeer.key, transfer))
//        @Suppress("UNCHECKED_CAST")
//        val outgoing = community.evaProtocol?.setAndReturnPrivateProperty("outgoing", outgoingMap) as MutableMap<Key, Transfer>

        assertEquals(null, community.myPeer.lastRequest)

        community.evaProtocol?.let { evaProtocol ->
            evaProtocol.javaClass.declaredMethods.first {
                it.name == "transmitData"
            }.let { method ->
                method.isAccessible = true

                method.invoke(evaProtocol, community.myPeer, transfer)

                assertNotEquals(null, community.myPeer.lastRequest)
            }
        }

        community.unload()
    }

    @Test
    fun onData_initializing() {
        val community = getCommunity()
        community.load()

        var progressState = ""
        community.setOnEVAReceiveProgressCallback { _, _, progress ->
            progressState = progress.state.name
        }

        val info = Community.EVAId.EVA_PEERCHAT_ATTACHMENT
        val id = "0123456789abcdefghijklmnopqrst"
        val nonce = (0..EVAProtocol.MAX_NONCE).random().toULong()
        val data = "Lorem ipsum dolor sit amet".toByteArray(Charsets.UTF_8)
        val blockCount = 6
        val blockSize = 5

        val scheduledTransfer = ScheduledTransfer(info, data, nonce, id, blockCount, data.size.toULong(), blockSize)
        val transfer = Transfer(TransferType.INCOMING, scheduledTransfer)
        transfer.windowSize = 2

        val incomingMap: MutableMap<Key, Transfer> = mutableMapOf(Pair(community.myPeer.key, transfer))
        @Suppress("UNCHECKED_CAST")
        val incoming = community.evaProtocol?.setAndReturnPrivateProperty("incoming", incomingMap) as MutableMap<Key, Transfer>
        assertEquals(1, incoming.size)

        val blockNumber = 0
        val payload = EVADataPayload(
            blockNumber,
            nonce,
            transfer.getData(blockNumber)
        )

        val unReceivedBlocksBeforeSize = incoming[community.myPeer.key]?.unReceivedBlocks?.size
        community.onEVAData(community.myPeer, payload)
        val unReceivedBlocksAfterSize = incoming[community.myPeer.key]?.unReceivedBlocks?.size

        assertNotEquals(unReceivedBlocksBeforeSize, unReceivedBlocksAfterSize)
        assertEquals("INITIALIZING", progressState)

        community.unload()
    }

    @Test
    fun onData_downloading() {
        val community = getCommunity()
        community.load()

        var progressState = ""
        community.setOnEVAReceiveProgressCallback { _, _, progress ->
            progressState = progress.state.name
        }

        val info = Community.EVAId.EVA_PEERCHAT_ATTACHMENT
        val id = "0123456789abcdefghijklmnopqrst"
        val nonce = (0..EVAProtocol.MAX_NONCE).random().toULong()
        val data = "Lorem ipsum dolor sit amet".toByteArray(Charsets.UTF_8)
        val blockCount = 6
        val blockSize = 5

        val scheduledTransfer = ScheduledTransfer(info, data, nonce, id, blockCount, data.size.toULong(), blockSize)
        val transfer = Transfer(TransferType.INCOMING, scheduledTransfer)
        transfer.windowSize = 2
        transfer.ackedWindow = 1

        val incomingMap: MutableMap<Key, Transfer> = mutableMapOf(Pair(community.myPeer.key, transfer))
        @Suppress("UNCHECKED_CAST")
        val incoming = community.evaProtocol?.setAndReturnPrivateProperty("incoming", incomingMap) as MutableMap<Key, Transfer>
        assertEquals(1, incoming.size)

        val blockNumber = 2
        val payload = EVADataPayload(
            blockNumber,
            nonce,
            transfer.getData(blockNumber)
        )

        // Check if transfer is updated and unreceived blocks set is reduced, meaning data processing started
        val updatedBefore = incoming[community.myPeer.key]?.updated
        val unReceivedBlocksBeforeSize = incoming[community.myPeer.key]?.unReceivedBlocks?.size
        community.onEVAData(community.myPeer, payload)
        val updatedAfter = incoming[community.myPeer.key]?.updated
        val unReceivedBlocksAfterSize = incoming[community.myPeer.key]?.unReceivedBlocks?.size

        assertNotEquals(updatedBefore, updatedAfter)
        assertNotEquals(unReceivedBlocksBeforeSize, unReceivedBlocksAfterSize)
        assertEquals("DOWNLOADING", progressState)

        community.unload()
    }

    @Test
    fun onError() {
        val community = getCommunity()
        community.load()

        var error = ""
        community.setOnEVAErrorCallback { _, exception ->
            error = exception.m
        }

        val payload = EVAErrorPayload(
            Community.EVAId.EVA_PEERCHAT_ATTACHMENT,
            "An error occurred"
        )

        val info = Community.EVAId.EVA_PEERCHAT_ATTACHMENT
        val id = "0123456789abcdefghijklmnopqrst"
        val nonce = (0..EVAProtocol.MAX_NONCE).random().toULong()
        val data = "Lorem ipsum dolor sit amet".toByteArray(Charsets.UTF_8)
        val blockCount = 6
        val blockSize = 5

        val scheduledTransfer = ScheduledTransfer(info, data, nonce, id, blockCount, data.size.toULong(), blockSize)
        val transfer = Transfer(TransferType.OUTGOING, scheduledTransfer)
        transfer.windowSize = 2

        val outgoingMap: MutableMap<Key, Transfer> = mutableMapOf(Pair(community.myPeer.key, transfer))
        @Suppress("UNCHECKED_CAST")
        val outgoing = community.evaProtocol?.setAndReturnPrivateProperty("outgoing", outgoingMap) as MutableMap<Key, Transfer>

        assertEquals(1, outgoing.size)
        community.onEVAError(community.myPeer, payload)
        assertEquals(0, outgoing.size)

        assertEquals("An error occurred", error)

        community.unload()
    }

    @Test
    fun sendAcknowledgement() {
        val community = getCommunity()
        community.load()

        val info = Community.EVAId.EVA_PEERCHAT_ATTACHMENT
        val id = "0123456789abcdefghijklmnopqrst"
        val nonce = (0..EVAProtocol.MAX_NONCE).random().toULong()
        val data = "Lorem ipsum dolor sit amet".toByteArray(Charsets.UTF_8)
        val blockCount = 6
        val blockSize = 5

        val scheduledTransfer = ScheduledTransfer(info, data, nonce, id, blockCount, data.size.toULong(), blockSize)
        val transfer = Transfer(TransferType.INCOMING, scheduledTransfer)
        transfer.windowSize = 2
        transfer.ackedWindow = 1

        val incomingMap: MutableMap<Key, Transfer> = mutableMapOf(Pair(community.myPeer.key, transfer))
        @Suppress("UNCHECKED_CAST")
        val incoming = community.evaProtocol?.setAndReturnPrivateProperty("incoming", incomingMap) as MutableMap<Key, Transfer>
        assertEquals(1, incoming.size)

        community.evaProtocol?.let { evaProtocol ->
            evaProtocol.javaClass.declaredMethods.first {
                it.name == "sendAcknowledgement"
            }.let { method ->
                method.isAccessible = true

                assertEquals(null, community.myPeer.lastRequest)
                method.invoke(evaProtocol, community.myPeer, transfer)
                assertNotEquals(null, community.myPeer.lastRequest)
            }
        }

        community.unload()
    }

    @Test
    fun finishIncomingTransfer_receive_callbacks() {
        val community = getCommunity()
        community.load()

        var progressState = ""
        community.setOnEVAReceiveProgressCallback { _, _, progress ->
            progressState = progress.state.name
        }

        var receiveFinished = false
        community.setOnEVAReceiveCompleteCallback { _, _, _, _ ->
            receiveFinished = true
        }

        val info = Community.EVAId.EVA_PEERCHAT_ATTACHMENT
        val id = "0123456789abcdefghijklmnopqrst"
        val nonce = (0..EVAProtocol.MAX_NONCE).random().toULong()
        val data = "Lorem ipsum dolor sit amet".toByteArray(Charsets.UTF_8)
        val blockCount = 6
        val blockSize = 5

        val scheduledTransfer = ScheduledTransfer(info, data, nonce, id, blockCount, data.size.toULong(), blockSize)
        val transfer = Transfer(TransferType.INCOMING, scheduledTransfer)

        @Suppress("UNCHECKED_CAST")
        val finishedIncoming = community.evaProtocol?.getPrivateProperty("finishedIncoming") as MutableMap<Key, MutableSet<String>>

        community.evaProtocol?.let { evaProtocol ->
            evaProtocol.javaClass.declaredMethods.first {
                it.name == "finishIncomingTransfer"
            }.let { method ->
                method.isAccessible = true

                assertEquals(0, finishedIncoming.size)
                assertEquals("", progressState)
                assertEquals(false, receiveFinished)
                method.invoke(evaProtocol, community.myPeer, transfer)

                assertEquals(1, finishedIncoming.size)
                assertEquals("FINISHED", progressState)
                assertEquals(true, receiveFinished)
            }
        }

        community.unload()
    }

    @Test
    fun finishOutgoingTransfer_sent_callback() {
        val community = getCommunity()
        community.load()

        var receiveFinished = false
        community.setOnEVASendCompleteCallback { _, _, _, _ ->
            receiveFinished = true
        }

        val info = Community.EVAId.EVA_PEERCHAT_ATTACHMENT
        val id = "0123456789abcdefghijklmnopqrst"
        val nonce = (0..EVAProtocol.MAX_NONCE).random().toULong()
        val data = "Lorem ipsum dolor sit amet".toByteArray(Charsets.UTF_8)
        val blockCount = 6
        val blockSize = 5

        val scheduledTransfer = ScheduledTransfer(info, data, nonce, id, blockCount, data.size.toULong(), blockSize)
        val transfer = Transfer(TransferType.OUTGOING, scheduledTransfer)

        @Suppress("UNCHECKED_CAST")
        val finishedOutgoing = community.evaProtocol?.getPrivateProperty("finishedOutgoing") as MutableMap<Key, MutableSet<String>>

        community.evaProtocol?.let { evaProtocol ->
            evaProtocol.javaClass.declaredMethods.first {
                it.name == "finishOutgoingTransfer"
            }.let { method ->
                method.isAccessible = true

                assertEquals(0, finishedOutgoing.size)
                assertEquals(false, receiveFinished)
                method.invoke(evaProtocol, community.myPeer, transfer)

                assertEquals(1, finishedOutgoing.size)
                assertEquals(true, receiveFinished)
            }
        }

        community.unload()
    }
}

fun <T : Any> T.getPrivateProperty(variableName: String): Any? {
    return javaClass.getDeclaredField(variableName).let { field ->
        field.isAccessible = true
        return@let field.get(this)
    }
}

fun <T : Any> T.setAndReturnPrivateProperty(variableName: String, data: Any): Any? {
    return javaClass.getDeclaredField(variableName).let { field ->
        field.isAccessible = true
        field.set(this, data)
        return@let field.get(this)
    }
}


