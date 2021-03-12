package nl.tudelft.ipv8.attestation.identity.payloads

import nl.tudelft.ipv8.messaging.*

class DisclosePayload(
    val metadata: ByteArray,
    val tokens: ByteArray,
    val attestations: ByteArray,
    val authorities: ByteArray,
) : Serializable {
    override fun serialize(): ByteArray {
        return serializeVarLen(metadata) + serializeVarLen(metadata) + serializeVarLen(attestations) + serializeVarLen(
            authorities)
    }

    companion object Deserializer : Deserializable<DisclosePayload> {
        override fun deserialize(buffer: ByteArray, offset: Int): Pair<DisclosePayload, Int> {
            var localOffset = offset
            val (metadata, offset1) = deserializeVarLen(buffer, localOffset)
            localOffset += offset1

            val (tokens, offset2) = deserializeVarLen(buffer, localOffset)
            localOffset += offset2

            val (attestations, offset3) = deserializeVarLen(buffer, localOffset)
            localOffset += offset3

            val (authorities, offset4) = deserializeVarLen(buffer, localOffset)
            localOffset += offset4

            return Pair(DisclosePayload(metadata, tokens, attestations, authorities), localOffset)
        }
    }

}
