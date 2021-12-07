package nl.tudelft.ipv8.messaging.eva

data class EVAWriteRequestPayload(
    val dataSize: Int,
    val blockCount: Int,
    val nonce: ULong,
    val id: String,
    val infoBinary: ByteArray
) {

}

data class EVAAcknowledgementPayload(
    val number: Int,
    val windowSize: Int,
    val nonce: ULong
) {

}

data class EVADataPayload(
    val blockNumber: Int,
    val nonce: ULong,
    val dataBinary: ByteArray
) {

}

data class EVAErrorPayload(
    val message: String,
    val info: String
) {

}
