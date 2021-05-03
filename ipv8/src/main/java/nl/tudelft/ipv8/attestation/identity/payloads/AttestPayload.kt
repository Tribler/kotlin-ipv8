package nl.tudelft.ipv8.attestation.identity.payloads

import nl.tudelft.ipv8.messaging.Deserializable
import nl.tudelft.ipv8.messaging.Serializable
import nl.tudelft.ipv8.messaging.deserializeVarLen
import nl.tudelft.ipv8.messaging.serializeVarLen

class AttestPayload(val attestation: ByteArray) : Serializable {
    override fun serialize(): ByteArray {
        return serializeVarLen(attestation)
    }

    companion object Deserializer : Deserializable<AttestPayload> {
        override fun deserialize(buffer: ByteArray, offset: Int): Pair<AttestPayload, Int> {
            val (deserialized, localOffset) = deserializeVarLen(buffer, offset)
            return Pair(AttestPayload(deserialized), offset + localOffset)
        }
    }

}
