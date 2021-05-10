  package nl.tudelft.ipv8.attestation.identity.payloads

import nl.tudelft.ipv8.messaging.Deserializable
import nl.tudelft.ipv8.messaging.SERIALIZED_SHA3_256_SIZE
import nl.tudelft.ipv8.messaging.SIGNATURE_SIZE
import nl.tudelft.ipv8.messaging.Serializable
import nl.tudelft.ipv8.messaging.deserializeSHA3_256

class AttestPayload(val attestation: ByteArray) : Serializable {
    override fun serialize(): ByteArray {
        return attestation
    }

    companion object Deserializer : Deserializable<AttestPayload> {
        override fun deserialize(buffer: ByteArray, offset: Int): Pair<AttestPayload, Int> {
            var localOffset = offset

            val attestation = buffer.copyOfRange(localOffset, localOffset + SERIALIZED_SHA3_256_SIZE + SIGNATURE_SIZE)
            localOffset += SERIALIZED_SHA3_256_SIZE + SIGNATURE_SIZE

            return Pair(AttestPayload(attestation), localOffset)
        }
    }
}
