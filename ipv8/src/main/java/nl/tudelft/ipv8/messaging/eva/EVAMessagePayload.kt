package nl.tudelft.ipv8.messaging.eva

import nl.tudelft.ipv8.Community
import nl.tudelft.ipv8.messaging.*

open class EVAMessagePayload(
    val type: Int
) : Serializable {
    override fun serialize(): ByteArray {
        return "".toByteArray()
    }
}

data class EVAWriteRequestPayload(
    var dataSize: Int,
    var blockCount: Int,
    var nonce: ULong,
    var id: String,
    var infoBinary: ByteArray
) : EVAMessagePayload(Community.MessageId.EVA_WRITE_REQUEST), Serializable {
    override fun serialize(): ByteArray {
        return serializeUShort(dataSize) +
            serializeUShort(blockCount) +
            serializeULong(nonce) +
            serializeVarLen(id.toByteArray(Charsets.UTF_8)) +
            infoBinary
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as EVAWriteRequestPayload

        if (dataSize != other.dataSize) return false
        if (blockCount != other.blockCount) return false
        if (nonce != other.nonce) return false
        if (id != other.id) return false
        if (!infoBinary.contentEquals(other.infoBinary)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = dataSize
        result = 31 * result + blockCount
        result = 31 * result + nonce.hashCode()
        result = 31 * result + id.hashCode()
        result = 31 * result + infoBinary.contentHashCode()
        return result
    }

    companion object Deserializer : Deserializable<EVAWriteRequestPayload> {
        override fun deserialize(buffer: ByteArray, offset: Int): Pair<EVAWriteRequestPayload, Int> {
            var localOffset = 0
            val dataSize = deserializeUShort(buffer, offset + localOffset)
            localOffset += SERIALIZED_USHORT_SIZE
            val blockCount = deserializeUShort(buffer, offset + localOffset)
            localOffset += SERIALIZED_USHORT_SIZE
            val nonce = deserializeULong(buffer, offset + localOffset)
            localOffset += SERIALIZED_ULONG_SIZE
            val (id, idLen) = deserializeVarLen(buffer, offset + localOffset)
            localOffset += idLen
            val (infoBinary, infoBinaryLen) = deserializeRaw(buffer, offset + localOffset)
            localOffset += infoBinaryLen

            val payload = EVAWriteRequestPayload(
                dataSize,
                blockCount,
                nonce,
                String(id, Charsets.UTF_8),
                infoBinary
            )

            return Pair(payload, localOffset)
        }
    }
}

data class EVAAcknowledgementPayload(
    var number: Int,
    var windowSize: Int,
    var nonce: ULong
) : EVAMessagePayload(Community.MessageId.EVA_ACKNOWLEDGEMENT), Serializable {
    override fun serialize(): ByteArray {
        return serializeUShort(number) +
            serializeUShort(windowSize) +
            serializeULong(nonce)
    }

    companion object Deserializer : Deserializable<EVAAcknowledgementPayload> {
        override fun deserialize(buffer: ByteArray, offset: Int): Pair<EVAAcknowledgementPayload, Int> {
            var localOffset = 0
            val number = deserializeUShort(buffer, offset + localOffset)
            localOffset += SERIALIZED_USHORT_SIZE
            val windowSize = deserializeUShort(buffer, offset + localOffset)
            localOffset += SERIALIZED_USHORT_SIZE
            val nonce = deserializeULong(buffer, offset + localOffset)
            localOffset += SERIALIZED_ULONG_SIZE

            val payload = EVAAcknowledgementPayload(
                number,
                windowSize,
                nonce
            )
            return Pair(payload, localOffset)
        }
    }
}

data class EVADataPayload(
    var blockNumber: Int,
    var nonce: ULong,
    var dataBinary: ByteArray
) : EVAMessagePayload(Community.MessageId.EVA_DATA), Serializable {
    override fun serialize(): ByteArray {
        return serializeUShort(blockNumber) +
            serializeULong(nonce) +
            dataBinary
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as EVADataPayload

        if (blockNumber != other.blockNumber) return false
        if (nonce != other.nonce) return false
        if (!dataBinary.contentEquals(other.dataBinary)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = blockNumber
        result = 31 * result + nonce.hashCode()
        result = 31 * result + dataBinary.contentHashCode()
        return result
    }

    companion object Deserializer : Deserializable<EVADataPayload> {
        override fun deserialize(buffer: ByteArray, offset: Int): Pair<EVADataPayload, Int> {
            var localOffset = 0
            val blockNumber = deserializeUShort(buffer, offset + localOffset)
            localOffset += SERIALIZED_USHORT_SIZE
            val nonce = deserializeULong(buffer, offset + localOffset)
            localOffset += SERIALIZED_ULONG_SIZE
            val (dataBinary, dataBinaryLen) = deserializeRaw(buffer, offset + localOffset)
            localOffset += dataBinaryLen

            val payload = EVADataPayload(
                blockNumber,
                nonce,
                dataBinary
            )

            return Pair(payload, localOffset)
        }
    }
}

data class EVAErrorPayload(
    var message: String,
    var info: String
) : EVAMessagePayload(Community.MessageId.EVA_ERROR), Serializable {
    override fun serialize(): ByteArray {
        return message.toByteArray(Charsets.UTF_8) +
            info.toByteArray(Charsets.UTF_8)
    }

    companion object Deserializer : Deserializable<EVAErrorPayload> {
        override fun deserialize(buffer: ByteArray, offset: Int): Pair<EVAErrorPayload, Int> {
            var localOffset = 0
            val (message, messageLen) = deserializeVarLen(buffer, offset + localOffset)
            localOffset += messageLen
            val (info, infoLen) = deserializeVarLen(buffer, offset + localOffset)
            localOffset += infoLen

            val payload = EVAErrorPayload(
                String(message, Charsets.UTF_8),
                String(info, Charsets.UTF_8)
            )

            return Pair(payload, localOffset)
        }
    }
}
