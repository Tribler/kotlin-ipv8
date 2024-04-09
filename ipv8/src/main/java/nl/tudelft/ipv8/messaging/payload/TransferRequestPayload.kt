package nl.tudelft.ipv8.messaging.payload

import nl.tudelft.ipv8.messaging.AutoSerializable
import nl.tudelft.ipv8.messaging.Deserializable
import nl.tudelft.ipv8.messaging.Serializable
import nl.tudelft.ipv8.messaging.simpleDeserialize

data class TransferRequestPayload(
    val filename: String,
    val status: TransferStatus,
    val type: TransferType,
    val dataSize: Int = 0
) : AutoSerializable {

    companion object Deserializer : Deserializable<TransferRequestPayload> {
        override fun deserialize(
            buffer: ByteArray,
            offset: Int
        ): Pair<TransferRequestPayload, Int> {
            val (filename, newOffset) = simpleDeserialize<String>(buffer, offset)
            val (status, newOffset1) = simpleDeserialize<TransferStatus>(buffer, newOffset)
            val (type, newOffset2) = simpleDeserialize<TransferType>(buffer, newOffset1)
            val (dataSize, newOffset3) = simpleDeserialize<Int>(buffer, newOffset2)
            return Pair(TransferRequestPayload(filename, status, type, dataSize), newOffset3)
        }
    }

    enum class TransferStatus {
        REQUEST,
        ACCEPT,
        DECLINE
    }

    enum class TransferType {
        FILE,
        RANDOM_DATA
    }
}
