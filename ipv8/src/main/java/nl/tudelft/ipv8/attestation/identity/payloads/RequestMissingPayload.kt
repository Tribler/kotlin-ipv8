package nl.tudelft.ipv8.attestation.identity.payloads

import nl.tudelft.ipv8.messaging.*

class RequestMissingPayload(
    val known: Int,
) : Serializable {
    override fun serialize(): ByteArray {
        return serializeUInt(known.toUInt())
    }

    companion object Deserializer : Deserializable<RequestMissingPayload> {
        override fun deserialize(buffer: ByteArray, offset: Int): Pair<RequestMissingPayload, Int> {
            val known = deserializeUInt(buffer, offset)
            return Pair(RequestMissingPayload(known.toInt()), offset + SERIALIZED_UINT_SIZE)
        }
    }

}
