package nl.tudelft.ipv8.attestation.wallet

import nl.tudelft.ipv8.messaging.Deserializable
import nl.tudelft.ipv8.messaging.Serializable
import nl.tudelft.ipv8.messaging.deserializeUShort
import nl.tudelft.ipv8.messaging.serializeUShort
import nl.tudelft.ipv8.util.hexToBytes


// TODO: Add default payload class
class RequestAttestationPayload(val metadata: String) : Serializable {
    private val msgId = 5
    private val formatList = arrayOf<String>("raw")


    override fun serialize(): ByteArray {
        return metadata.hexToBytes()
    }

    companion object Deserializer : Deserializable<RequestAttestationPayload> {
        override fun deserialize(
            buffer: ByteArray,
            offset: Int
        ): Pair<RequestAttestationPayload, Int> {
            return Pair(RequestAttestationPayload(buffer.toString()), buffer.size)
        }

    }

}
