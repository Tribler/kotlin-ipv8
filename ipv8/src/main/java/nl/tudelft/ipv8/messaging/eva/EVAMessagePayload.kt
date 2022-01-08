package nl.tudelft.ipv8.messaging.eva

import nl.tudelft.ipv8.Community
import nl.tudelft.ipv8.messaging.*

open class EVAMessagePayload(
    val type: Int
)

data class EVAWriteRequestPayload(
    var info: String,
    var id: String,
    var nonce: ULong,
    var dataSize: ULong,
    var blockCount: UInt,
    var blockSize: UInt,
    var windowSize: UInt,
) : EVAMessagePayload(Community.MessageId.EVA_WRITE_REQUEST), Serializable {
    override fun serialize(): ByteArray {
        return serializeVarLen(info.toByteArray(Charsets.UTF_8)) +
            serializeVarLen(id.toByteArray(Charsets.UTF_8)) +
            serializeULong(nonce) +
            serializeULong(dataSize) +
            serializeUInt(blockCount) +
            serializeUInt(blockSize) +
            serializeUInt(windowSize)
    }

    companion object Deserializer : Deserializable<EVAWriteRequestPayload> {
        override fun deserialize(buffer: ByteArray, offset: Int): Pair<EVAWriteRequestPayload, Int> {
            var localOffset = 0
            val (info, infoLen) = deserializeVarLen(buffer, offset + localOffset)
            localOffset += infoLen
            val (id, idLen) = deserializeVarLen(buffer, offset + localOffset)
            localOffset += idLen
            val nonce = deserializeULong(buffer, offset + localOffset)
            localOffset += SERIALIZED_ULONG_SIZE
            val dataSize = deserializeULong(buffer, offset + localOffset)
            localOffset += SERIALIZED_ULONG_SIZE
            val blockCount = deserializeUInt(buffer, offset + localOffset)
            localOffset += SERIALIZED_UINT_SIZE
            val blockSize = deserializeUInt(buffer, offset + localOffset)
            localOffset += SERIALIZED_UINT_SIZE
            val windowSize = deserializeUInt(buffer, offset + localOffset)
            localOffset += SERIALIZED_UINT_SIZE

            val payload = EVAWriteRequestPayload(
                String(info, Charsets.UTF_8),
                String(id, Charsets.UTF_8),
                nonce,
                dataSize,
                blockCount,
                blockSize,
                windowSize
            )

            return Pair(payload, localOffset)
        }
    }
}

data class EVAAcknowledgementPayload(
    var nonce: ULong,
    var ackWindow: UInt,
    var unAckedBlocks: ByteArray
) : EVAMessagePayload(Community.MessageId.EVA_ACKNOWLEDGEMENT), Serializable {
    override fun serialize(): ByteArray {
        return serializeULong(nonce) +
            serializeUInt(ackWindow) +
            unAckedBlocks
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as EVAAcknowledgementPayload

        if (nonce != other.nonce) return false
        if (ackWindow != other.ackWindow) return false
        if (!unAckedBlocks.contentEquals(other.unAckedBlocks)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = nonce.hashCode()
        result = 31 * result + ackWindow.hashCode()
        result = 31 * result + unAckedBlocks.contentHashCode()
        return result
    }


    companion object Deserializer : Deserializable<EVAAcknowledgementPayload> {
        override fun deserialize(buffer: ByteArray, offset: Int): Pair<EVAAcknowledgementPayload, Int> {
            var localOffset = 0
            val nonce = deserializeULong(buffer, offset + localOffset)
            localOffset += SERIALIZED_ULONG_SIZE
            val ackWindow = deserializeUInt(buffer, offset + localOffset)
            localOffset += SERIALIZED_UINT_SIZE
            val (unAckedBlocks, unAckedBlocksLen) = deserializeRaw(buffer, offset + localOffset)
            localOffset += unAckedBlocksLen

            val payload = EVAAcknowledgementPayload(
                nonce,
                ackWindow,
                unAckedBlocks
            )
            return Pair(payload, localOffset)
        }
    }
}

data class EVADataPayload(
    var blockNumber: UInt,
    var nonce: ULong,
    var data: ByteArray
) : EVAMessagePayload(Community.MessageId.EVA_DATA), Serializable {
    override fun serialize(): ByteArray {
        return serializeUInt(blockNumber) +
            serializeULong(nonce) +
            data
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as EVADataPayload

        if (blockNumber != other.blockNumber) return false
        if (nonce != other.nonce) return false
        if (!data.contentEquals(other.data)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = blockNumber.hashCode()
        result = 31 * result + nonce.hashCode()
        result = 31 * result + data.contentHashCode()
        return result
    }

    companion object Deserializer : Deserializable<EVADataPayload> {
        override fun deserialize(buffer: ByteArray, offset: Int): Pair<EVADataPayload, Int> {
            var localOffset = 0
            val blockNumber = deserializeUInt(buffer, offset + localOffset)
            localOffset += SERIALIZED_UINT_SIZE
            val nonce = deserializeULong(buffer, offset + localOffset)
            localOffset += SERIALIZED_ULONG_SIZE
            val (data, dataLen) = deserializeRaw(buffer, offset + localOffset)
            localOffset += dataLen

            val payload = EVADataPayload(
                blockNumber,
                nonce,
                data
            )

            return Pair(payload, localOffset)
        }
    }
}

data class EVAErrorPayload(
    var info: String,
    var message: String
) : EVAMessagePayload(Community.MessageId.EVA_ERROR), Serializable {
    override fun serialize(): ByteArray {
        return serializeVarLen(info.toByteArray(Charsets.UTF_8)) +
            serializeVarLen(message.toByteArray(Charsets.UTF_8))
    }

    companion object Deserializer : Deserializable<EVAErrorPayload> {
        override fun deserialize(buffer: ByteArray, offset: Int): Pair<EVAErrorPayload, Int> {
            var localOffset = 0
            val (info, infoLen) = deserializeVarLen(buffer, offset + localOffset)
            localOffset += infoLen
            val (message, messageLen) = deserializeVarLen(buffer, offset + localOffset)
            localOffset += messageLen

            val payload = EVAErrorPayload(
                info.toString(Charsets.UTF_8),
                message.toString(Charsets.UTF_8)
            )

            return Pair(payload, localOffset)
        }
    }
}
