package nl.tudelft.ipv8.attestation.revocation.payloads

import nl.tudelft.ipv8.messaging.*
import nl.tudelft.ipv8.util.hexToBytes
import nl.tudelft.ipv8.util.toHex
import java.lang.RuntimeException

class RevocationUpdateChunkPayload(
    val hash: ByteArray,
    val sequenceNumber: Int,
    val data: ByteArray,
) : Serializable {
    override fun serialize(): ByteArray {
        return hash + serializeUInt(sequenceNumber.toUInt()) + serializeVarLen(data)
    }

    companion object Deserializer : Deserializable<RevocationUpdateChunkPayload> {
        override fun deserialize(buffer: ByteArray, offset: Int): Pair<RevocationUpdateChunkPayload, Int> {
            var localOffset = offset
            val hash = buffer.copyOfRange(localOffset, localOffset + SERIALIZED_SHA1_HASH_SIZE)
            localOffset += SERIALIZED_SHA1_HASH_SIZE

            val sequenceNumber = deserializeUInt(buffer, localOffset).toInt()
            localOffset += SERIALIZED_UINT_SIZE

            val (data, dataOffset) = deserializeVarLen(buffer, localOffset)

            return Pair(RevocationUpdateChunkPayload(hash, sequenceNumber, data), localOffset + dataOffset)
        }
    }

}

