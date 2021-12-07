package nl.tudelft.ipv8.messaging.eva

import java.util.*

data class Transfer(
    private val type: TransferType,
    private val scheduledTransfer: ScheduledTransfer
) {
    var infoBinary: ByteArray? = scheduledTransfer.infoBinary
    var dataBinary: ByteArray? = scheduledTransfer.dataBinary
    var nonce = scheduledTransfer.nonce
    var id = scheduledTransfer.id

    var dataSize = scheduledTransfer.dataBinary.size
    var blockNumber = NONE
    var blockCount = scheduledTransfer.blockCount
    var attempt = 0
    var windowSize = 0
    var acknowledgementNumber = 0
    var updated = Date().time
    var released = false

    private val progressMarkers = (0..100).associateBy { kotlin.math.ceil(scheduledTransfer.blockCount.toDouble() / 100 * it).toInt() }

    fun release() {
        infoBinary = null
        dataBinary = null
        released = true
    }

    fun isProgressMarker(): Boolean {
        return getProgressMarker() != null
    }

    fun getProgressMarker(): Double? {
        return progressMarkers[blockNumber]?.toDouble()
    }

    override fun toString(): String {
        return "Type: $type. Info: '${infoBinary.contentToString()}'. Block: $blockNumber($blockCount). Window size: $windowSize. Updated: $updated. Nonce: $nonce"
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
    val infoBinary: ByteArray,
    val dataBinary: ByteArray,
    val nonce: ULong,
    val id: String,
    val blockCount: Int
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ScheduledTransfer

        if (!infoBinary.contentEquals(other.infoBinary)) return false
        if (!dataBinary.contentEquals(other.dataBinary)) return false
        if (nonce != other.nonce) return false
        if (id != other.id) return false


        return true
    }

    override fun hashCode(): Int {
        var result = infoBinary.contentHashCode()
        result = 31 * result + dataBinary.contentHashCode()
        result = 31 * result + nonce.hashCode()
        result = 31 * result + id.hashCode()
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
