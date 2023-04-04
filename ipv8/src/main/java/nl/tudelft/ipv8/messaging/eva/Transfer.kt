package nl.tudelft.ipv8.messaging.eva

import java.util.*

data class Transfer(
    val type: TransferType,
    private val scheduledTransfer: ScheduledTransfer
) {
    var info = scheduledTransfer.info
    var id = scheduledTransfer.id
    var data = ByteArray(scheduledTransfer.dataSize.toInt())
    var nonce = scheduledTransfer.nonce

    var dataSize = scheduledTransfer.dataSize
    var blockCount = scheduledTransfer.blockCount
    var blockSize = scheduledTransfer.blockSize

    var attempt = 0
    var windowSize = scheduledTransfer.windowSize
    var ackedWindow = 0
    var updated = Date().time
    var released = false

    // Set of all blocks that have not been received by the receiver or have been sent by the sender
    var unReceivedBlocks: MutableSet<Int> = when (type) {
        TransferType.INCOMING -> (0 until scheduledTransfer.blockCount).toMutableSet()
        else -> mutableSetOf()
    }

    var lastSentBlocks: List<Int> = listOf()

    fun release() {
        info = ""
        data = byteArrayOf()
        released = true
    }

    /**
     * Used by the sender.
     * Determines which block numbers must be send in the next send-wave.
     */
    fun setSendSet(): List<Int> {
        val list = ((ackedWindow * windowSize)..(kotlin.math.min(
            (ackedWindow + 1) * windowSize - 1,
            blockCount - 1
        ))).toSet()
        lastSentBlocks = (unReceivedBlocks + list).toList()
        return lastSentBlocks
    }

    /**
     * Used by the receiver.
     * Upon receipt of data the data is placed at the correct position in the byte array.
     * The block number is removed from the set of unreceived blocks.
     */
    fun addData(number: Int, data: ByteArray) {
        val startIndex = number * blockSize
        data.copyInto(this.data, startIndex)
        removeUnreceivedBlock(number)
    }

    /**
     * Used by the sender.
     * Fetches the data for a particular block.
     */
    fun getData(blockNumber: Int): ByteArray {
        val start = blockNumber * blockSize
        val stop = start + blockSize
        return data.takeInRange(start, stop)
    }

    /**
     * Used by the sender.
     * Upon receipt of an ack from the receiver it includes a list of block numbers that hasn't been
     * received yet. These block numbers are added to the set of unreceived block numbers to be
     * included in the next send-wave.
     */
    fun addUnreceivedBlocks(set: ByteArray) {
        set.decodeToIntegerList()
            .forEach {
                unReceivedBlocks.add(it)
            }
    }

    /**
     * Used by the receiver.
     * Removes a block (number) from the set of unreceived blocks
     */
    private fun removeUnreceivedBlock(number: Int) {
        unReceivedBlocks.remove(number)
    }

    /**
     * Used by the receiver.
     * Get the unreceived blocks within the acked windows
     */
    fun getUnreceivedBlocksUntil(): ByteArray {
        return unReceivedBlocks
            .filter { it < ackedWindow * windowSize }
            .encodeToByteArray()
    }

    /**
     * Used by the receiver.
     * Returns whether a block has been received.
     */
    fun isBlockReceived(number: Int): Boolean = !unReceivedBlocks.contains(number)

    /**
     * Used by the receiver.
     * Function that determines the progress of the receiving process by counting the number of
     * received blocks compared to the total blockcount
     */
    fun getProgress(): Double =
        100.0 - (unReceivedBlocks.size.toDouble() / blockCount.toDouble()) * 100.0

    override fun toString(): String {
        return "Type: '$type'. Info: '$info'. ID: '$id'. Data size: '$dataSize'. Block size: '$blockSize'. Block count: '$blockCount'. Window size: '$windowSize'. Updated: '$updated'. Nonce: '$nonce'. AckWindow: '$ackedWindow'."
    }

    companion object {
        const val NONE = -1
    }
}

enum class TransferType {
    INCOMING,
    OUTGOING
}

data class ScheduledTransfer(
    val info: String,
    val data: ByteArray,
    val nonce: ULong,
    val id: String,
    val blockCount: Int,
    val dataSize: ULong,
    val blockSize: Int,
    val windowSize: Int
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ScheduledTransfer

        if (info != other.info) return false
        if (!data.contentEquals(other.data)) return false
        if (nonce != other.nonce) return false
        if (id != other.id) return false
        if (blockCount != other.blockCount) return false
        if (dataSize != other.dataSize) return false
        if (blockSize != other.blockSize) return false
        if (windowSize != other.windowSize) return false

        return true
    }

    override fun hashCode(): Int {
        var result = info.hashCode()
        result = 31 * result + data.contentHashCode()
        result = 31 * result + nonce.hashCode()
        result = 31 * result + id.hashCode()
        result = 31 * result + blockCount
        result = 31 * result + dataSize.hashCode()
        result = 31 * result + blockSize
        result = 31 * result + windowSize
        return result
    }

}

data class TransferProgress(
    val id: String,
    val state: TransferState,
    val progress: Double
)

enum class TransferState {
    SCHEDULED,
    INITIALIZING,
    DOWNLOADING,
    STOPPED,
    FINISHED,
    UNKNOWN
}

fun CharSequence.splitIgnoreEmpty(vararg delimiters: String): List<String> {
    return this.split(*delimiters).filter {
        it.isNotEmpty()
    }
}

// TODO: Make compatible with py-ipv8
fun ByteArray.decodeToIntegerList(): List<Int> {
    val list = mutableListOf<Int>()

    var i = 0
    while (i < size) {
        list.add(
            (this[i + 3].toInt() shl 24) or
                (this[i + 2].toInt() and 0xff shl 16) or
                (this[i + 1].toInt() and 0xff shl 8) or
                (this[i].toInt() and 0xff)
        )

        i += 4
    }

    return list
}

// TODO: Make compatible with py-ipv8
fun List<Int>.encodeToByteArray(): ByteArray {
    val byteArray = ByteArray(size * 4)

    var i = 0
    for (number in this) {
        byteArray[i] = (number shr 0).toByte()
        byteArray[i + 1] = (number shr 8).toByte()
        byteArray[i + 2] = (number shr 16).toByte()
        byteArray[i + 3] = (number shr 24).toByte()

        i += 4
    }

    return byteArray
}
