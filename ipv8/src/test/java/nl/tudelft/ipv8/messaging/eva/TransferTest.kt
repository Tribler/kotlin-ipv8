package nl.tudelft.ipv8.messaging.eva

import nl.tudelft.ipv8.Community
import org.junit.Assert.*
import org.junit.Test

class TransferTest {
    private val info = Community.EVAId.EVA_PEERCHAT_ATTACHMENT
    private val id = "0123456789abcdefghijklmnopqrst"
    private val nonce = (0..EVAProtocol.MAX_NONCE).random().toULong()
    private val data = "Lorem ipsum dolor sit amet".toByteArray(Charsets.UTF_8)
    private val blockCount = 6
    private val blockSize = 5
    private val windowSize = 2

    private fun createScheduledTransfer() = ScheduledTransfer(
        info,
        data,
        nonce,
        id,
        blockCount,
        data.size.toULong(),
        blockSize,
        windowSize
    )

    fun createIncomingTransfer() = Transfer(
        TransferType.INCOMING,
        createScheduledTransfer()
    )

    private fun createOutgoingTransfer() = Transfer(
        TransferType.OUTGOING,
        createScheduledTransfer()
    )

    @Test
    fun scheduledTransfer_correctness() {
        val scheduledTransfer = createScheduledTransfer()
        assertEquals(info, scheduledTransfer.info)
        assertArrayEquals(data, scheduledTransfer.data)
        assertEquals(nonce, scheduledTransfer.nonce)
        assertEquals(id, scheduledTransfer.id)
        assertEquals(blockCount, scheduledTransfer.blockCount)
        assertEquals(blockSize, scheduledTransfer.blockSize)
    }

    @Test
    fun scheduledTransfer_equals() {
        val scheduledTransfer = createScheduledTransfer()
        val transfer = createIncomingTransfer()
        val scheduledTransferDifferentInfo = scheduledTransfer.copy(info = "DIFFERENT")
        val scheduledTransferDifferentData = scheduledTransfer.copy(data = byteArrayOf())
        val scheduledTransferDifferentNonce = scheduledTransfer.copy(nonce = 100L.toULong())
        val scheduledTransferDifferentId = scheduledTransfer.copy(id = "123")
        val scheduledTransferDifferentBlockCount = scheduledTransfer.copy(blockCount = 2)
        val scheduledTransferDifferentDataSize = scheduledTransfer.copy(dataSize = 2.toULong())
        val scheduledTransferDifferentBlockSize = scheduledTransfer.copy(blockSize = 2)
        val scheduledTransferDifferentWindowSize = scheduledTransfer.copy(windowSize = 20)

        assertEquals(true, scheduledTransfer.equals(scheduledTransfer))
        assertEquals(scheduledTransfer::class.java, scheduledTransferDifferentInfo::class.java)
        assertNotEquals(scheduledTransfer::class.java, transfer::class.java)
        assertEquals(false, scheduledTransfer.equals(transfer))
        assertEquals(false, scheduledTransfer.equals(scheduledTransferDifferentInfo))
        assertEquals(false, scheduledTransfer.equals(scheduledTransferDifferentData))
        assertEquals(false, scheduledTransfer.equals(scheduledTransferDifferentNonce))
        assertEquals(false, scheduledTransfer.equals(scheduledTransferDifferentId))
        assertEquals(false, scheduledTransfer.equals(scheduledTransferDifferentBlockCount))
        assertEquals(false, scheduledTransfer.equals(scheduledTransferDifferentDataSize))
        assertEquals(false, scheduledTransfer.equals(scheduledTransferDifferentBlockSize))
        assertEquals(false, scheduledTransfer.equals(scheduledTransferDifferentWindowSize))
    }

    @Test
    fun scheduledTransfer_hashCode() {
        val scheduledTransfer = createScheduledTransfer()
        val scheduledTransferCopy = createScheduledTransfer()
        val scheduledTransferOther = scheduledTransfer.copy(info = "DIFFERENT")

        assertEquals(true, scheduledTransfer.equals(scheduledTransferCopy) && scheduledTransferCopy.equals(scheduledTransfer))
        assertEquals(scheduledTransfer.hashCode(), scheduledTransferCopy.hashCode())

        assertEquals(false, scheduledTransfer.equals(scheduledTransferOther) && scheduledTransferOther.equals(scheduledTransfer))
        assertNotEquals(scheduledTransfer.hashCode(), scheduledTransferOther.hashCode())
    }

    @Test
    fun transfer_correctness() {
        val transfer = createIncomingTransfer()
        transfer.windowSize = 5
        transfer.ackedWindow = 2
        assertEquals(info, transfer.info)
        assertArrayEquals(ByteArray(data.size), transfer.data)
        assertEquals(nonce, transfer.nonce)
        assertEquals(id, transfer.id)
        assertEquals(blockCount, transfer.blockCount)
        assertEquals(blockSize, transfer.blockSize)
        assertEquals(data.size, transfer.dataSize.toInt())
        assertEquals(0, transfer.lastSentBlocks.size)
        assertEquals(5, transfer.windowSize)
        assertEquals(2, transfer.ackedWindow)
    }

    @Test
    fun transfer_incoming_correctness() {
        val transfer = createIncomingTransfer()
        assertEquals(TransferType.INCOMING, transfer.type)
        assertEquals(blockCount, transfer.unReceivedBlocks.size)
    }

    @Test
    fun transfer_outgoing_correctness() {
        val transfer = createOutgoingTransfer()
        assertEquals(TransferType.OUTGOING, transfer.type)
        assertEquals(0, transfer.unReceivedBlocks.size)
    }

    @Test
    fun transfer_release() {
        val transfer = createOutgoingTransfer()
        transfer.data = data
        assertEquals(true, transfer.info != "")
        assertEquals(data.size, transfer.data.size)
        assertEquals(false, transfer.released)

        transfer.release()

        assertEquals(false, transfer.info != "")
        assertEquals(0, transfer.data.size)
        assertEquals(true, transfer.released)
    }

    @Test
    fun transfer_outgoing_setSendSet() {
        val transfer = createOutgoingTransfer()
        transfer.windowSize = 5
        transfer.setSendSet()
        val sendList = listOf(0, 1, 2, 3, 4)

        assertEquals(sendList.size, transfer.lastSentBlocks.size)
        assertArrayEquals(sendList.toTypedArray(), transfer.lastSentBlocks.toTypedArray())
    }

    @Test
    fun transfer_incoming_addData() {
        val transfer = createIncomingTransfer()
        val unReceivedBlocks = transfer.unReceivedBlocks
        assertEquals(unReceivedBlocks.size, blockCount)
        unReceivedBlocks.remove(0)
        assertEquals(unReceivedBlocks.size, blockCount - 1)

        transfer.addData(0, data.copyOfRange(0, 4))

        assertEquals(transfer.data[0], data[0])
        assertEquals(transfer.data[1], data[1])
        assertEquals(transfer.data[2], data[2])
        assertEquals(transfer.data[3], data[3])
        assertNotEquals(transfer.data[4], data[4])

        assertArrayEquals(unReceivedBlocks.toTypedArray(), transfer.unReceivedBlocks.toTypedArray())
    }

    @Test
    fun transfer_outgoing_getData() {
        val transfer = createOutgoingTransfer()
        transfer.data = data
        val firstBlockData = transfer.getData(0)
        val secondBlockData = transfer.getData(1)
        val lastBlockData = transfer.getData(5)

        assertArrayEquals(data.copyOfRange(0, blockSize), firstBlockData)
        assertArrayEquals(data.copyOfRange(1 * blockSize, blockSize * 2), secondBlockData)
        assertEquals(1, lastBlockData.size)
        assertArrayEquals(data.copyOfRange(25, 26), lastBlockData)
    }

    @Test
    fun transfer_outgoing_addUnreceivedBlocks() {
        val transfer = createOutgoingTransfer()
        transfer.data = data

        assertEquals(0, transfer.unReceivedBlocks.size)
        val list = listOf(0, 2, 5).encodeToByteArray()
        transfer.addUnreceivedBlocks(list)
        assertEquals(3, transfer.unReceivedBlocks.size)
    }

    @Test
    fun transfer_incoming_removeUnreceivedBlock() {
        val transfer = createIncomingTransfer()

        assertEquals(blockCount, transfer.unReceivedBlocks.size)

        transfer.javaClass.getDeclaredMethod("removeUnreceivedBlock", Int::class.java).let { method ->
            method.isAccessible = true
            method.invoke(transfer, 2)
        }
        assertEquals(blockCount - 1, transfer.unReceivedBlocks.size)
        assertEquals(false, transfer.unReceivedBlocks.contains(2))
    }

    @Test
    fun transfer_incoming_getUnreceivedBlocksUntil() {
        val transfer = createIncomingTransfer()
        transfer.windowSize = 5
        transfer.ackedWindow = 1
        transfer.addData(0, data.copyOfRange(0, blockSize))
        transfer.addData(2, data.copyOfRange(2 * blockSize, blockSize * 3))

        assertArrayEquals(listOf(1, 3, 4).encodeToByteArray().toTypedArray(), transfer.getUnreceivedBlocksUntil().toTypedArray())
        assertEquals(true, transfer.isBlockReceived(0))
        assertEquals(false, transfer.isBlockReceived(1))
        assertEquals(true, transfer.isBlockReceived(2))
        assertEquals(false, transfer.isBlockReceived(3))
        assertEquals(false, transfer.isBlockReceived(4))
    }

    @Test
    fun transfer_incoming_getProgress() {
        val transfer = createIncomingTransfer()
        assertEquals(0.toFloat(), transfer.getProgress().toFloat())

        transfer.addData(0, data.copyOfRange(0, blockSize))
        transfer.addData(1, data.copyOfRange(blockSize, blockSize * 2))
        transfer.addData(2, data.copyOfRange(2 * blockSize, blockSize * 3))

        assertEquals(50.toFloat(), transfer.getProgress().toFloat())
    }

    @Test
    fun transferProgress_create() {
        val transferProgress = TransferProgress(
            "0123456789",
            TransferState.DOWNLOADING,
            50.0
        )

        assertEquals("0123456789", transferProgress.id)
        assertEquals(TransferState.DOWNLOADING, transferProgress.state)
        assertEquals(50.0.toFloat(), transferProgress.progress.toFloat())
    }
}
