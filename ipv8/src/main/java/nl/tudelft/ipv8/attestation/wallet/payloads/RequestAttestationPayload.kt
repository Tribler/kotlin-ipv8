package nl.tudelft.ipv8.attestation.wallet.payloads

import nl.tudelft.ipv8.messaging.Deserializable
import nl.tudelft.ipv8.messaging.Serializable
import nl.tudelft.ipv8.messaging.deserializeVarLen
import nl.tudelft.ipv8.messaging.serializeVarLen


// TODO: Add default payload class
class RequestAttestationPayload(val metadata: String) : Serializable {
    private val msgId = 5

    override fun serialize(): ByteArray {
        return serializeVarLen(metadata.toByteArray(Charsets.US_ASCII))
    }

    companion object Deserializer : Deserializable<RequestAttestationPayload> {
        override fun deserialize(
            buffer: ByteArray,
            offset: Int
        ): Pair<RequestAttestationPayload, Int> {
            val (metadata, metadataSize) = deserializeVarLen(buffer, offset)
            return Pair(RequestAttestationPayload(metadata.toString(Charsets.US_ASCII)),
                metadataSize)
        }

    }

}
