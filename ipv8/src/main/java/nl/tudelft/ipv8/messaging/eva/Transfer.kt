package nl.tudelft.ipv8.messaging.eva

data class Transfer(
    val type: TransferType,
    val scheduledTransfer: ScheduledTransfer
) {
    var infoBinary = scheduledTransfer.infoBinary
    var dataBinary = scheduledTransfer.dataBinary
    val nonce = scheduledTransfer.nonce

    var windowSize = 0
    var blockNumber = NONE


    companion object {
        const val NONE = -1
    }
}

data class ScheduledTransfer(
    val infoBinary: ByteArray,
    val dataBinary: ByteArray,
    val nonce: Long
) {

}

data class TransferProgress(
    val id: String,
    val state: TransferState,
    val progress: Double
) {

//    val progressMarkers = (0..100).blockNumber..

    fun isProgressMarkers(): Boolean {
        ..
    }

//    fun getProgressMarker(): ..

//    val number2digits = 205000.0
//    val solution = number2digits.div(1000).toBigDecimal().setScale(0, RoundingMode.UP)
//    println(solution)



}

enum class TransferState {
    SCHEDULED,
    INITIALIZING,
    DOWNLOADING,
    FINISHED
}

enum class TransferType {
    OUTGOING,
    INCOMING
}
