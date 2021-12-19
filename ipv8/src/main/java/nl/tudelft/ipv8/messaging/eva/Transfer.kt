package nl.tudelft.ipv8.messaging.eva

import mu.KotlinLogging
import java.util.*

private val logger = KotlinLogging.logger {}

data class Transfer(
    private val type: TransferType,
    private val scheduledTransfer: ScheduledTransfer
) {
    var info = scheduledTransfer.info

    //    var dataBinary: ByteArray? = scheduledTransfer.dataBinary
    var dataBinary = ByteArray(scheduledTransfer.dataSize.toInt())
    var nonce = scheduledTransfer.nonce
    var id = scheduledTransfer.id
    var dataSize = scheduledTransfer.dataSize
    var blockCount = scheduledTransfer.blockCount

    var blockNumber = NONE
    var attempt = 0
    var windowSize = 0
    var acknowledgementNumber = 0
    var ackedWindow = 0
    var updated = Date().time
    var released = false
    val blockSize = 1000

    // Set of all blocks that have not been received by the receiver or have been sent by the sender
    var unReceivedBlocks: MutableSet<Int> = when (type) {
        TransferType.INCOMING -> (0 until scheduledTransfer.blockCount).toMutableSet()
        else -> mutableSetOf()
    }

    var lastSendBlocks: List<Int> = listOf()

//    private val progressMarkers = (0..100).associateBy { kotlin.math.ceil(scheduledTransfer.blockCount.toDouble() / 100 * it).toInt() }

    fun release() {
        info = ""
        dataBinary = byteArrayOf()
        released = true
    }

    fun setSendSet() {
        val list = ((ackedWindow * windowSize)..(kotlin.math.min(
            (ackedWindow + 1) * windowSize - 1,
            blockCount - 1
        ))).toSet()
        lastSendBlocks = (unReceivedBlocks + list).toList()
    }

    /**
     * Used by the receiver.
     * Upon receipt of data the data is placed at the correct position in the byte array.
     * The block number is removed from the set of unreceived blocks.
     */
    fun addData(number: Int, data: ByteArray) {
        val startIndex = number * blockSize
        data.copyInto(dataBinary, startIndex)
        removeUnreceivedBlock(number)
    }

    /**
     * Used by the sender.
     * Fetches the data
     */
    fun getData(blockNumber: Int): ByteArray {
        val start = blockNumber * blockSize
        val stop = start + blockSize
        return dataBinary.takeInRange(start, stop)
    }

    /**
     * Used by the sender.
     * Upon receipt of an ack from the receiver, the unreceived blocks within the acked windows
     * are re-added to the set of unreceived blocks.
     */
    fun addUnreceivedBlocks(set: ByteArray) {
        val collection: MutableCollection<Int> = mutableSetOf()
        set
            .decodeToString()
            .removeSurrounding("[", "]")
            .replace(" ", "")
            .splitIgnoreEmpty(",")
            .map { it.toInt() }
            .toCollection(collection)
        unReceivedBlocks.addAll(collection)

//        logger.debug { "EVAPROTOCOL: BLOCK NUMBERS: $blockNumbers" }
//
//        if (blockNumbers.isNotEmpty()) {
//            logger.debug { "EVAPROTOCOL: BLOCKNUMBERS NOT EMPTY!" }
//            try {
//                blockNumbers.map { it.toInt() }
//                    .toCollection(collection)
//                unReceivedBlocks.addAll(collection)
//            } catch (e: Exception) {
//                e.printStackTrace()
//            }
//        }
    }

    fun removeUnreceivedBlock(number: Int) {
        unReceivedBlocks.remove(number)
    }

    /**
     * Used by the receiver.
     * Get the unreceived blocks within the acked windows
     */
    fun getUnreceivedBlocksUntil(): ByteArray {
        return unReceivedBlocks
            .filter { it < ackedWindow * windowSize }
            .map { it.toString() }
            .toTypedArray()
            .contentToString()
            .encodeToByteArray()
    }

    /**
     * Function that determines the progress of the receiving process by counting the number of
     * received blocks compared to the total blockcount
     */
    fun getProgress(): Double =
        100.0 - (unReceivedBlocks.size.toDouble() / blockCount.toDouble()) * 100.0

//    fun isProgressMarker(): Boolean {
//        return getProgressMarker() != null
//    }
//
//    fun getProgressMarker(): Double? {
//        return progressMarkers[blockNumber]?.toDouble()
//    }

    override fun toString(): String {
        return "Type: $type. Info: '$info'. Data size: $dataSize, Block: $blockNumber($blockCount). Window size: $windowSize. Updated: $updated. Nonce: $nonce"
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
    val dataBinary: ByteArray,
    val nonce: ULong,
    val id: String,
    val blockCount: Int,
    val dataSize: ULong
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ScheduledTransfer

        if (info != other.info) return false
        if (!dataBinary.contentEquals(other.dataBinary)) return false
        if (nonce != other.nonce) return false
        if (id != other.id) return false
        if (blockCount != other.blockCount) return false
        if (dataSize != other.dataSize) return false

        return true
    }

    override fun hashCode(): Int {
        var result = info.hashCode()
        result = 31 * result + dataBinary.contentHashCode()
        result = 31 * result + nonce.hashCode()
        result = 31 * result + id.hashCode()
        result = 31 * result + blockCount
        result = 31 * result + dataSize.hashCode()
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
    FINISHED
}

fun CharSequence.splitIgnoreEmpty(vararg delimiters: String): List<String> {
    return this.split(*delimiters).filter {
        it.isNotEmpty()
    }
}
