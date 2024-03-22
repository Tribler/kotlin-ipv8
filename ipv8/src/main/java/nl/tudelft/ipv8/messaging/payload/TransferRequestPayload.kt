package nl.tudelft.ipv8.messaging.payload

import nl.tudelft.ipv8.messaging.Deserializable
import nl.tudelft.ipv8.messaging.SERIALIZED_LONG_SIZE
import nl.tudelft.ipv8.messaging.SERIALIZED_UINT_SIZE
import nl.tudelft.ipv8.messaging.Serializable
import nl.tudelft.ipv8.messaging.deserializeLong
import nl.tudelft.ipv8.messaging.deserializeUInt
import nl.tudelft.ipv8.messaging.serializeLong
import nl.tudelft.ipv8.messaging.serializeUChar
import nl.tudelft.ipv8.messaging.serializeUInt

class TransferRequestPayload(
    val port: Int,
    val status: TransferStatus,
    var dataSize: Int = 0
): Serializable {
    override fun serialize(): ByteArray {
        return serializeLong(port.toLong()) + serializeUInt(status.ordinal.toUInt()) + serializeLong(dataSize.toLong())
    }

    companion object Deserializer : Deserializable<TransferRequestPayload> {
        override fun deserialize(
            buffer: ByteArray,
            offset: Int
        ): Pair<TransferRequestPayload, Int> {
            var localOffset = offset

            val port = deserializeLong(buffer, localOffset)
            localOffset += SERIALIZED_LONG_SIZE
            val status = deserializeUInt(buffer, localOffset)
            localOffset += SERIALIZED_UINT_SIZE
            val dataSize = deserializeLong(buffer, localOffset)
            localOffset += SERIALIZED_LONG_SIZE

            return Pair(TransferRequestPayload(port.toInt(),
                TransferStatus.entries[status.toInt()], dataSize.toInt()), localOffset)
        }
    }

    enum class TransferStatus {
        REQUEST,
        ACCEPT,
        DECLINE
    }

}
