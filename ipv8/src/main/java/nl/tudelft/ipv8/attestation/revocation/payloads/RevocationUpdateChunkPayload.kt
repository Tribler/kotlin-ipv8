package nl.tudelft.ipv8.attestation.revocation.payloads

import nl.tudelft.ipv8.messaging.*

class RevocationUpdateChunkPayload(
    val sequenceNumber: Int,
    val payloadHash: ByteArray,
    val authorityKeyHash: ByteArray,
    val version: Long,
    val data: ByteArray,
) : Serializable {
    override fun serialize(): ByteArray {
        return serializeUInt(sequenceNumber.toUInt()) + payloadHash + authorityKeyHash + serializeULong(version.toULong()) + serializeVarLen(
            data)
    }

    companion object Deserializer : Deserializable<RevocationUpdateChunkPayload> {
        override fun deserialize(buffer: ByteArray, offset: Int): Pair<RevocationUpdateChunkPayload, Int> {
            var localOffset = offset

            val sequenceNumber = deserializeUInt(buffer, localOffset).toInt()
            localOffset += SERIALIZED_UINT_SIZE

            val hash = buffer.copyOfRange(localOffset, localOffset + SERIALIZED_SHA1_HASH_SIZE)
            localOffset += SERIALIZED_SHA1_HASH_SIZE

            val publicKeyHash = buffer.copyOfRange(localOffset, localOffset + SERIALIZED_SHA1_HASH_SIZE)
            localOffset += SERIALIZED_SHA1_HASH_SIZE

            val version = deserializeULong(buffer, localOffset).toLong()
            localOffset += SERIALIZED_ULONG_SIZE

            val (data, dataOffset) = deserializeVarLen(buffer, localOffset)

            return Pair(
                RevocationUpdateChunkPayload(sequenceNumber, hash, publicKeyHash, version, data),
                localOffset + dataOffset
            )
        }
    }

}

