package nl.tudelft.ipv8.attestation.wallet.payloads

import nl.tudelft.ipv8.messaging.*

class AttestationChunkPayload(
    val hash: ByteArray,
    val sequenceNumber: Int,
    val data: ByteArray,
    val metadata: ByteArray? = null,
    val signature: ByteArray? = null,
) : Serializable {
    private val msgId = 2

    override fun serialize(): ByteArray {
        return (
            hash + serializeUInt(sequenceNumber.toUInt()) + serializeVarLen(data) +
                (
                    if (metadata != null && signature != null) serializeVarLen(metadata) + serializeVarLen(signature)
                    else byteArrayOf()
                    )
            )
    }

    companion object Deserializer : Deserializable<AttestationChunkPayload> {
        override fun deserialize(
            buffer: ByteArray,
            offset: Int,
        ): Pair<AttestationChunkPayload, Int> {
            var localoffset = 0

            val hash = buffer.copyOfRange(offset + localoffset,
                offset + localoffset + SERIALIZED_SHA1_HASH_SIZE)
            localoffset += SERIALIZED_SHA1_HASH_SIZE

            val sequenceNumber = deserializeUInt(buffer, offset + localoffset)
            localoffset += SERIALIZED_UINT_SIZE

            val (data, dataSize) = deserializeVarLen(buffer, offset + localoffset)
            localoffset += dataSize

            return if (buffer.lastIndex > offset + localoffset) {
                val (metadata, metadataSize) = deserializeVarLen(buffer, offset + localoffset)
                localoffset += metadataSize

                val (signature, signatureSize) = deserializeVarLen(buffer, offset + localoffset)
                localoffset += signatureSize

                val payload = AttestationChunkPayload(hash, sequenceNumber.toInt(), data, metadata, signature)
                Pair(payload, localoffset)
            } else {
                val payload = AttestationChunkPayload(hash, sequenceNumber.toInt(), data)
                Pair(payload, localoffset)
            }

        }

    }
}
