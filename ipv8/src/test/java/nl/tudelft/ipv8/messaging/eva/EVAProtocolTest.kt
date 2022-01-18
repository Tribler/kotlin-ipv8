package nl.tudelft.ipv8.messaging.eva

import io.mockk.mockk
import kotlinx.coroutines.*
import kotlinx.coroutines.test.runBlockingTest
import nl.tudelft.ipv8.*
import nl.tudelft.ipv8.keyvault.Key
import nl.tudelft.ipv8.keyvault.defaultCryptoProvider
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

    private fun createMockEVAWriteRequestPayload() = EVAWriteRequestPayload(
        Community.EVAId.EVA_PEERCHAT_ATTACHMENT,
        "0123456789",
        1234.toULong(),
        10000.toULong(),
        10.toUInt(),
        EVAProtocol.BLOCK_SIZE.toUInt(),
        EVAProtocol.WINDOW_SIZE_IN_BLOCKS.toUInt()
    )

    private fun createScheduledTransfer(id: String = "0123456789abcdefghijklmnopqrst"): ScheduledTransfer {
        val info = Community.EVAId.EVA_PEERCHAT_ATTACHMENT
        val nonce = (0..EVAProtocol.MAX_NONCE).random().toULong()
        val data = "Lorem ipsum dolor sit amet".toByteArray(Charsets.UTF_8)
        val blockCount = 6
        val blockSize = 5
        val windowSize = EVAProtocol.WINDOW_SIZE_IN_BLOCKS

        return ScheduledTransfer(info, data, nonce, id, blockCount, data.size.toULong(), blockSize, windowSize)
    }

    @Suppress("UNCHECKED_CAST")
    fun Community.getIncoming(): MutableMap<Key, Transfer> {
        return evaProtocol?.getPrivateProperty("incoming") as MutableMap<Key, Transfer>
    }

    @Suppress("UNCHECKED_CAST")
    fun Community.setIncoming(map: MutableMap<Key, Transfer>): MutableMap<Key, Transfer> {
        return evaProtocol?.setAndReturnPrivateProperty("incoming", map) as MutableMap<Key, Transfer>
    }

    @Suppress("UNCHECKED_CAST")
    fun Community.getOutgoing(): MutableMap<Key, Transfer> {
        return evaProtocol?.getPrivateProperty("outgoing") as MutableMap<Key, Transfer>
    }

    @Suppress("UNCHECKED_CAST")
    fun Community.setOutgoing(map: MutableMap<Key, Transfer>): MutableMap<Key, Transfer> {
        return evaProtocol?.setAndReturnPrivateProperty("outgoing", map) as MutableMap<Key, Transfer>
    }

    @Suppress("UNCHECKED_CAST")
    fun Community.getScheduled(): MutableMap<Key, Queue<ScheduledTransfer>> {
        return evaProtocol?.getPrivateProperty("scheduled") as MutableMap<Key, Queue<ScheduledTransfer>>
    }

    @Suppress("UNCHECKED_CAST")
    fun Community.setScheduled(map: MutableMap<Key, Transfer>): MutableMap<Key, Queue<ScheduledTransfer>> {
        return evaProtocol?.setAndReturnPrivateProperty("scheduled", map) as MutableMap<Key, Queue<ScheduledTransfer>>
    }

    @Suppress("UNCHECKED_CAST")
    fun Community.getFinishedIncoming(): MutableMap<Key, MutableSet<String>> {
        return evaProtocol?.getPrivateProperty("finishedIncoming") as MutableMap<Key, MutableSet<String>>
    }

    @Suppress("UNCHECKED_CAST")
    fun Community.getFinishedOutgoing(): MutableMap<Key, MutableSet<String>> {
        return evaProtocol?.getPrivateProperty("finishedOutgoing") as MutableMap<Key, MutableSet<String>>
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
                    10.toUInt(),
                    EVAProtocol.BLOCK_SIZE.toUInt(),
                    EVAProtocol.WINDOW_SIZE_IN_BLOCKS.toUInt()
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

        var transferState = ""
        community.setOnEVAReceiveProgressCallback { _, _, progress ->
            transferState = progress.state.name
        }

        val mockPeer = Peer(mockk())

        val scheduled = community.getScheduled()
        assertEquals(0, scheduled.size)
        community.evaProtocol?.sendBinary(
            mockPeer,
            Community.EVAId.EVA_PEERCHAT_ATTACHMENT,
            "012345678",
            byteArrayOf(0, 1, 2, 3, 4, 5)
        )

        assertEquals(1, scheduled.size)
        assertEquals(TransferState.SCHEDULED.name, transferState)

        community.unload()
    }

    @Test
    fun startOutgoingTransfer_scheduled() {
        val community = getCommunity()
        community.load()

        var transferState = ""
        community.setOnEVAReceiveProgressCallback { _, _, progress ->
            transferState = progress.state.name
        }

        val mockPeer = Peer(mockk())

        val scheduled = community.getScheduled()

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
                assertEquals(TransferState.SCHEDULED.name, transferState)
            }
        }

        community.unload()
    }

    @Test
    fun startOutgoingTransfer_send() {
        val community = getCommunity()
        community.load()

        assertEquals(0, community.getPeers().size)

        val address = IPv4Address("1.2.3.4", 1234)
        val peer = Peer(defaultCryptoProvider.generateKey(), address)
        community.network.addVerifiedPeer(peer)
        community.network.discoverServices(peer, listOf(community.serviceId))

        assertEquals(1, community.getPeers().size)

        community.evaProtocol?.let { evaProtocol ->
            evaProtocol.javaClass.declaredMethods.first {
                it.name == "startOutgoingTransfer"
            }.let { method ->
                method.isAccessible = true

                val lastRequest = peer.lastRequest
                assertEquals(0, community.getScheduled().size)
                assertEquals(0, community.getOutgoing().size)
                method.invoke(
                    evaProtocol,
                    peer,
                    Community.EVAId.EVA_PEERCHAT_ATTACHMENT,
                    "012345678",
                    byteArrayOf(0, 1, 2, 3, 4, 5),
                    1234
                )

                assertEquals(0, community.getScheduled().size)
                assertEquals(1, community.getOutgoing().size)

                assertNotEquals(lastRequest, peer.lastRequest)
            }
        }

        community.unload()
    }

    @Test
    fun startOutgoingTransfer_datasize_over_limit() = runBlocking {
        val community = getCommunity()
        community.load()

        // Initialize eva protocol with smaller binary size limit
        val binarySizeLimit = 1000000
        val scope = CoroutineScope(Dispatchers.Main)
        community.evaProtocol = EVAProtocol(community, scope, binarySizeLimit = binarySizeLimit)

        var error = ""
        community.setOnEVAErrorCallback { _, exception ->
            error = exception.m
        }

        assertEquals(0, community.getPeers().size)

        val address = IPv4Address("1.2.3.4", 1234)
        val peer = Peer(defaultCryptoProvider.generateKey(), address)
        community.network.addVerifiedPeer(peer)
        community.network.discoverServices(peer, listOf(community.serviceId))

        assertEquals(1, community.getPeers().size)

        community.evaProtocol?.let { evaProtocol ->
            evaProtocol.javaClass.declaredMethods.first {
                it.name == "startOutgoingTransfer"
            }.let { method ->
                method.isAccessible = true

                val lastRequest = peer.lastRequest
                assertEquals(0, community.getScheduled().size)
                assertEquals(0, community.getOutgoing().size)
                method.invoke(
                    evaProtocol,
                    peer,
                    Community.EVAId.EVA_PEERCHAT_ATTACHMENT,
                    "012345678",
                    ByteArray(binarySizeLimit + 1),
                    1234
                )

                assertEquals(0, community.getScheduled().size)
                assertEquals(0, community.getOutgoing().size)

                assertEquals(error, "Current data size limit($binarySizeLimit) has been exceeded")

                assertEquals(lastRequest, peer.lastRequest)
            }
        }

        scope.cancel()

        community.unload()
    }

    @Test
    fun onWriteRequest_datasize_zero() {
        val community = getCommunity()
        community.load()

        var error = ""
        var exp: TransferException? = null
        community.setOnEVAErrorCallback { _, exception ->
            error = exception.m
            exp = exception
        }

        val payload = createMockEVAWriteRequestPayload()
        payload.dataSize = 0u

        val lastRequest = community.myPeer.lastRequest
        community.onEVAWriteRequest(community.myPeer, payload)

        // Error packet should be sent, meaning last request shouldn't be empty anymore
        val nextRequest = community.myPeer.lastRequest
        assertEquals(null, lastRequest)
        assertNotEquals(null, nextRequest)

        // Error callback should have been called as well
        assertEquals(error, "Data size can not be less or equal to 0")
        assertEquals(true, exp != null && exp is SizeException)

        community.unload()
    }

    @Test
    fun onWriteRequest_datasize_over_limit() {
        val community = getCommunity()
        community.load()

        var error = ""
        var exp: TransferException? = null
        community.setOnEVAErrorCallback { _, exception ->
            error = exception.m
            exp = exception
        }

        val dataSize = EVAProtocol.BINARY_SIZE_LIMIT

        val payload = createMockEVAWriteRequestPayload()
        payload.dataSize = (dataSize + 1).toULong()

        val lastRequest = community.myPeer.lastRequest
        community.onEVAWriteRequest(community.myPeer, payload)

        // Error packet should be sent, meaning last request shouldn't be empty anymore
        val nextRequest = community.myPeer.lastRequest
        assertEquals(null, lastRequest)
        assertNotEquals(null, nextRequest)

        // Error callback should have been called
        assertEquals(error, "Current data size limit($dataSize) has been exceeded")
        assertEquals(true, exp != null && exp is SizeException)

        community.unload()
    }

    @Test
    fun onWriteRequest_peer_busy() {
        val community = getCommunity()
        community.load()

        var error = ""
        var exp: TransferException? = null
        community.setOnEVAErrorCallback { _, exception ->
            error = exception.m
            exp = exception
        }

        val payload = createMockEVAWriteRequestPayload()

        val transfer = Transfer(TransferType.OUTGOING, createScheduledTransfer())
        val outgoingMap: MutableMap<Key, Transfer> = mutableMapOf(Pair(community.myPeer.key, transfer))
        val outgoing = community.setOutgoing(outgoingMap)
        assertEquals(1, outgoing.size)

        val lastRequest = community.myPeer.lastRequest
        community.onEVAWriteRequest(community.myPeer, payload)

        // Error packet should be sent, meaning last request shouldn't be empty anymore
        val nextRequest = community.myPeer.lastRequest
        assertEquals(null, lastRequest)
        assertNotEquals(null, nextRequest)

        // Error callback should have been called
        assertEquals(error, "There is already a transfer ongoing with peer")
        assertEquals(true, exp != null && exp is PeerBusyException)

        community.unload()
    }

    @Test
    fun onWriteRequest() {
        val community = getCommunity()
        community.load()

        val incoming = community.getIncoming()

        assertEquals(0, incoming.size)
        community.onEVAWriteRequest(community.myPeer, createMockEVAWriteRequestPayload())
        assertEquals(1, incoming.size)

        community.unload()
    }

    @Test
    fun onAcknowledgement() {
        val community = getCommunity()
        community.load()

        val transfer = Transfer(TransferType.OUTGOING, createScheduledTransfer())
        transfer.ackedWindow = 1

        val payload = EVAAcknowledgementPayload(
            transfer.nonce,
            2.toUInt(),
            listOf(1, 2).encodeToByteArray()
        )

        val outgoingMap: MutableMap<Key, Transfer> = mutableMapOf(Pair(community.myPeer.key, transfer))
        val outgoing = community.setOutgoing(outgoingMap)
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

        val transfer = Transfer(TransferType.OUTGOING, createScheduledTransfer())
        transfer.windowSize = 5
        transfer.ackedWindow = 1
        transfer.setSendSet()

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

        val transfer = Transfer(TransferType.INCOMING, createScheduledTransfer())
        transfer.windowSize = 2

        val incomingMap: MutableMap<Key, Transfer> = mutableMapOf(Pair(community.myPeer.key, transfer))
        val incoming = community.setIncoming(incomingMap)
        assertEquals(1, incoming.size)

        val blockNumber = 0
        val payload = EVADataPayload(
            blockNumber.toUInt(),
            transfer.nonce,
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

        val transfer = Transfer(TransferType.INCOMING, createScheduledTransfer())
        transfer.windowSize = 2
        transfer.ackedWindow = 1
        transfer.updated = transfer.updated - 1

        val updatedBefore = transfer.updated
        val incomingMap: MutableMap<Key, Transfer> = mutableMapOf(Pair(community.myPeer.key, transfer))
        val incoming = community.setIncoming(incomingMap)
        assertEquals(1, incoming.size)

        val unReceivedBlocksBeforeSize = incoming[community.myPeer.key]?.unReceivedBlocks?.size

        val blockNumber = 2
        val payload = EVADataPayload(
            blockNumber.toUInt(),
            transfer.nonce,
            transfer.getData(blockNumber)
        )

        // Check if transfer is updated and unreceived blocks set is reduced, meaning data processing started
        community.onEVAData(community.myPeer, payload)
        val unReceivedBlocksAfterSize = incoming[community.myPeer.key]?.unReceivedBlocks?.size
        val updatedAfter = incoming[community.myPeer.key]?.updated

        assertNotEquals(updatedBefore, updatedAfter)
        assertNotEquals(unReceivedBlocksBeforeSize, unReceivedBlocksAfterSize)
        assertEquals("DOWNLOADING", progressState)

        community.unload()
    }

    @Test
    fun onData_last_block() {
        val community = getCommunity()
        community.load()

        val transfer = Transfer(TransferType.INCOMING, createScheduledTransfer())
        transfer.windowSize = 2
        transfer.ackedWindow = 1
        transfer.unReceivedBlocks = mutableSetOf(5)
        transfer.updated = transfer.updated - 1

        val updatedBefore = transfer.updated
        val incomingMap: MutableMap<Key, Transfer> = mutableMapOf(Pair(community.myPeer.key, transfer))
        val incoming = community.setIncoming(incomingMap)

        assertEquals(1, incoming.size)

        val blockNumber = 5
        val payload = EVADataPayload(
            blockNumber.toUInt(),
            transfer.nonce,
            transfer.getData(blockNumber)
        )

        assertEquals(1, incoming[community.myPeer.key]?.unReceivedBlocks?.size)
        assertEquals(1, community.getIncoming().size)
        assertEquals(0, community.getFinishedIncoming().size)
        assertEquals(null, community.myPeer.lastRequest)

        community.onEVAData(community.myPeer, payload)

        assertNotEquals(updatedBefore, incoming[community.myPeer.key]?.updated)
        assertEquals(0, community.getIncoming().size)
        assertEquals(1, community.getFinishedIncoming().size)
        assertNotEquals(null, community.myPeer.lastRequest)

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

        val transfer = Transfer(TransferType.OUTGOING, createScheduledTransfer())
        transfer.windowSize = 2

        val outgoingMap: MutableMap<Key, Transfer> = mutableMapOf(Pair(community.myPeer.key, transfer))
        val outgoing = community.setOutgoing(outgoingMap)

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

        val transfer = Transfer(TransferType.INCOMING, createScheduledTransfer())
        transfer.windowSize = 2
        transfer.ackedWindow = 1

        val incomingMap: MutableMap<Key, Transfer> = mutableMapOf(Pair(community.myPeer.key, transfer))
        val incoming = community.setIncoming(incomingMap)
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

        val transfer = Transfer(TransferType.INCOMING, createScheduledTransfer())

        val finishedIncoming = community.getFinishedIncoming()

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
        community.setOnEVASendCompleteCallback { _, _, _ ->
            receiveFinished = true
        }

        val transfer = Transfer(TransferType.OUTGOING, createScheduledTransfer())

        val finishedOutgoing = community.getFinishedOutgoing()

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

    @Test
    fun terminateByTimeout() {
        val community = getCommunity()
        community.load()

        var error = ""
        community.setOnEVAErrorCallback { _, exception ->
            error = exception.m
        }

        val transfer = Transfer(TransferType.OUTGOING, createScheduledTransfer())
        transfer.updated = Date().time - EVAProtocol.TIMEOUT_INTERVAL_IN_SEC * 1000 - 1000

        community.evaProtocol?.let { evaProtocol ->
            evaProtocol.javaClass.declaredMethods.first {
                it.name == "terminateByTimeout"
            }.let { method ->
                method.isAccessible = true

                method.invoke(evaProtocol, mutableMapOf<Key, Transfer>(), community.myPeer, transfer)

                assertEquals("Terminated by timeout. Timeout is ${EVAProtocol.TIMEOUT_INTERVAL_IN_SEC} sec", error)
            }
        }

        community.unload()
    }

    @Test
    fun terminateByTimeout_remaining_time() {
        val community = getCommunity()
        community.load()

        var error = ""
        community.setOnEVAErrorCallback { _, exception ->
            error = exception.m
        }

        val transfer = Transfer(TransferType.OUTGOING, createScheduledTransfer())
        transfer.updated = Date().time - EVAProtocol.TIMEOUT_INTERVAL_IN_SEC * 1000 / 2

        community.evaProtocol?.let { evaProtocol ->
            @Suppress("UNCHECKED_CAST")
            val scheduleTasks = evaProtocol.getPrivateProperty("scheduledTasks") as PriorityQueue<ScheduledTask>
            assertEquals(0, scheduleTasks.size)

            evaProtocol.javaClass.declaredMethods.first {
                it.name == "terminateByTimeout"
            }.let { method ->
                method.isAccessible = true

                method.invoke(evaProtocol, mutableMapOf<Key, Transfer>(), community.myPeer, transfer)

                assertEquals("", error)
                assertEquals(1, scheduleTasks.size)
            }
        }

        community.unload()
    }

    @Test
    fun resendAcknowledge() {
        val community = getCommunity()
        community.load()

        val transfer = Transfer(TransferType.OUTGOING, createScheduledTransfer())
        transfer.updated = Date().time - EVAProtocol.TIMEOUT_INTERVAL_IN_SEC * 1000 - 1000

        community.evaProtocol?.let { evaProtocol ->
            @Suppress("UNCHECKED_CAST")
            val scheduledTasks = evaProtocol.getPrivateProperty("scheduledTasks") as PriorityQueue<ScheduledTask>
            assertEquals(0, scheduledTasks.size)

            evaProtocol.javaClass.declaredMethods.first {
                it.name == "resendAcknowledge"
            }.let { method ->
                method.isAccessible = true

                method.invoke(evaProtocol, community.myPeer, transfer)

                assertEquals(1, scheduledTasks.size)
            }
        }

        community.unload()
    }

    @Test
    fun sendScheduled() {
        val community = getCommunity()
        community.load()

        assertEquals(0, community.getPeers().size)

        val address = IPv4Address("1.2.3.4", 1234)
        val peer = Peer(defaultCryptoProvider.generateKey(), address)
        community.network.addVerifiedPeer(peer)
        community.network.discoverServices(peer, listOf(community.serviceId))

        assertEquals(1, community.getPeers().size)

        val scheduledTransfer = createScheduledTransfer()

        community.evaProtocol?.let { evaProtocol ->
            val scheduled = community.getScheduled()
            assertEquals(0, scheduled.size)
            scheduled.addValue(peer.key, scheduledTransfer)
            assertEquals(1, community.getScheduled().size)

            evaProtocol.javaClass.declaredMethods.first {
                it.name == "sendScheduled"
            }.let { method ->
                method.isAccessible = true

                val outgoing = community.getOutgoing()
                assertEquals(0, outgoing.size)
                method.invoke(evaProtocol)
                assertEquals(0, scheduled.size)
                assertEquals(1, outgoing.size)
            }

        }

        community.unload()
    }

    @Test
    fun isScheduled_true() {
        val community = getCommunity()
        community.load()

        val key = community.myPeer.key
        val value = createScheduledTransfer()

        assertEquals(0, community.getScheduled().size)
        community.getScheduled().addValue(key, value)
        assertEquals(1, community.getScheduled().size)

        community.evaProtocol?.let { evaProtocol ->
            evaProtocol.javaClass.declaredMethods.first {
                it.name == "isScheduled"
            }.let { method ->
                method.isAccessible = true

                val result = method.invoke(evaProtocol, key, value.id)
                assertEquals(true, result)
            }
        }

        community.unload()
    }

    @Test
    fun isScheduled_false() {
        val community = getCommunity()
        community.load()

        val key = community.myPeer.key
        val value = createScheduledTransfer()

        assertEquals(0, community.getScheduled().size)

        community.evaProtocol?.let { evaProtocol ->
            evaProtocol.javaClass.declaredMethods.first {
                it.name == "isScheduled"
            }.let { method ->
                method.isAccessible = true

                val result = method.invoke(evaProtocol, key, value.id)
                assertEquals(false, result)
            }
        }

        community.unload()
    }

    @Test
    fun isOutgoing_true() {
        val community = getCommunity()
        community.load()

        val key = community.myPeer.key
        val value = Transfer(TransferType.OUTGOING, createScheduledTransfer())

        assertEquals(0, community.getOutgoing().size)
        community.setOutgoing(mutableMapOf(Pair(key, value)))
        assertEquals(1, community.getOutgoing().size)

        community.evaProtocol?.let { evaProtocol ->
            evaProtocol.javaClass.declaredMethods.first {
                it.name == "isOutgoing"
            }.let { method ->
                method.isAccessible = true

                val result = method.invoke(evaProtocol, key, value.id)
                assertEquals(true, result)
            }
        }

        community.unload()
    }

    @Test
    fun isOutgoing_false() {
        val community = getCommunity()
        community.load()

        val key = community.myPeer.key
        val value = Transfer(TransferType.OUTGOING, createScheduledTransfer())

        assertEquals(0, community.getOutgoing().size)

        community.evaProtocol?.let { evaProtocol ->
            evaProtocol.javaClass.declaredMethods.first {
                it.name == "isOutgoing"
            }.let { method ->
                method.isAccessible = true

                val result = method.invoke(evaProtocol, key, value.id)
                assertEquals(false, result)
            }
        }

        community.unload()
    }

    @Test
    fun isIncoming_true() {
        val community = getCommunity()
        community.load()

        val key = community.myPeer.key
        val value = Transfer(TransferType.OUTGOING, createScheduledTransfer())

        assertEquals(0, community.getIncoming().size)
        community.setIncoming(mutableMapOf(Pair(key, value)))
        assertEquals(1, community.getIncoming().size)

        community.evaProtocol?.let { evaProtocol ->
            evaProtocol.javaClass.declaredMethods.first {
                it.name == "isIncoming"
            }.let { method ->
                method.isAccessible = true

                val result = method.invoke(evaProtocol, key, value.id)
                assertEquals(true, result)
            }
        }

        community.unload()
    }

    @Test
    fun isIncoming_false() {
        val community = getCommunity()
        community.load()

        val key = community.myPeer.key
        val value = Transfer(TransferType.OUTGOING, createScheduledTransfer())

        assertEquals(0, community.getIncoming().size)

        community.evaProtocol?.let { evaProtocol ->
            evaProtocol.javaClass.declaredMethods.first {
                it.name == "isIncoming"
            }.let { method ->
                method.isAccessible = true

                val result = method.invoke(evaProtocol, key, value.id)
                assertEquals(false, result)
            }
        }

        community.unload()
    }

    @Test
    fun isTransferred_true() {
        val community = getCommunity()
        community.load()

        val key = community.myPeer.key
        val id = "0123456789"
        val container: MutableMap<Key, MutableSet<String>> = mutableMapOf()
        assertEquals(0, container.size)
        container.add(key, id)
        assertEquals(1, container.size)

        community.evaProtocol?.let { evaProtocol ->
            evaProtocol.javaClass.declaredMethods.first {
                it.name == "isTransferred"
            }.let { method ->
                method.isAccessible = true

                val result = method.invoke(evaProtocol, key, id, container)
                assertEquals(true, result)
            }
        }

        community.unload()
    }

    @Test
    fun isTransferred_false() {
        val community = getCommunity()
        community.load()

        val key = community.myPeer.key
        val id = "0123456789"
        val container: MutableMap<Key, MutableSet<String>> = mutableMapOf()
        assertEquals(0, container.size)

        community.evaProtocol?.let { evaProtocol ->
            evaProtocol.javaClass.declaredMethods.first {
                it.name == "isTransferred"
            }.let { method ->
                method.isAccessible = true

                val result = method.invoke(evaProtocol, key, id, container)
                assertEquals(false, result)
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


