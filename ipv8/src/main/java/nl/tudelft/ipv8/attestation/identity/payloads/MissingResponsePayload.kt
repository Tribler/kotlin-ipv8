package nl.tudelft.ipv8.attestation.identity.payloads

import nl.tudelft.ipv8.messaging.*

class MissingResponsePayload(
    val tokens: ByteArray,
) : Serializable {
    override fun serialize(): ByteArray {
        return tokens
    }

    companion object Deserializer : Deserializable<MissingResponsePayload> {
        override fun deserialize(buffer: ByteArray, offset: Int): Pair<MissingResponsePayload, Int> {
            val (tokens, localOffset) = deserializeRaw(buffer, offset)
            return Pair(MissingResponsePayload(tokens), offset + localOffset)
        }
    }

}
