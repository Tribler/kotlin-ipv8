package nl.tudelft.ipv8.attestation.wallet.payloads

import nl.tudelft.ipv8.messaging.*

class ChallengePayload(
    val attestationHash: ByteArray,
    val challenge: ByteArray,
) : Serializable {
    val messageId = 3

    override fun serialize(): ByteArray {
        return attestationHash + serializeVarLen(challenge)
    }

    companion object Deserializer : Deserializable<ChallengePayload> {
        override fun deserialize(
            buffer: ByteArray,
            offset: Int,
        ): Pair<ChallengePayload, Int> {
            var localoffset = 0

            val challengeHash = buffer.copyOfRange(offset + localoffset,
                offset + localoffset + SERIALIZED_SHA1_HASH_SIZE)
            localoffset += SERIALIZED_SHA1_HASH_SIZE

            val (data, dataSize) = deserializeVarLen(buffer, offset + localoffset)
            localoffset += dataSize

            val payload = ChallengePayload(challengeHash, data)
            return Pair(payload, localoffset)
        }

    }
}
