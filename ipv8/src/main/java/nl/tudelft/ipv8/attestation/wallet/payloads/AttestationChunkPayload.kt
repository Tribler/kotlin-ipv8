package nl.tudelft.ipv8.attestation.wallet.payloads

import nl.tudelft.ipv8.messaging.*

class AttestationChunkPayload(
    val hash: ByteArray,
    val sequenceNumber: Int,
    val data: ByteArray,
) : Serializable {

    override fun serialize(): ByteArray {
        return hash + serializeUInt(sequenceNumber.toUInt()) + serializeVarLen(data)
    }

    companion object Deserializer : Deserializable<AttestationChunkPayload> {
        override fun deserialize(
            buffer: ByteArray,
            offset: Int,
        ): Pair<AttestationChunkPayload, Int> {
            var localoffset = 0

            val hash = buffer.copyOfRange(
                offset + localoffset,
                offset + localoffset + SERIALIZED_SHA1_HASH_SIZE
            )
            localoffset += SERIALIZED_SHA1_HASH_SIZE

            val sequenceNumber = deserializeUInt(buffer, offset + localoffset)
            localoffset += SERIALIZED_UINT_SIZE

            val (data, dataSize) = deserializeVarLen(buffer, offset + localoffset)
            localoffset += dataSize

            val payload = AttestationChunkPayload(hash, sequenceNumber.toInt(), data)
            return Pair(payload, localoffset)
        }
    }
}
