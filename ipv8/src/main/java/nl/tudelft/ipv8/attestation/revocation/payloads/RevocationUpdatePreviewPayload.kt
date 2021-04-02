package nl.tudelft.ipv8.attestation.revocation.payloads

import nl.tudelft.ipv8.messaging.*
import nl.tudelft.ipv8.util.*

open class RevocationUpdatePreviewPayload(
    val revocationRefs: Map<ByteArrayKey, Long>,
) : Serializable {
    override fun serialize(): ByteArray {

        var out = byteArrayOf()
        revocationRefs.forEach {
            out += it.key.bytes
            out += serializeULong(it.value.toULong())
        }
        return serializeUInt(revocationRefs.size.toUInt()) + out
    }

    companion object Deserializer : Deserializable<RevocationUpdatePreviewPayload> {
        override fun deserialize(buffer: ByteArray, offset: Int): Pair<RevocationUpdatePreviewPayload, Int> {
            var localOffset = offset
            val size = deserializeUInt(buffer, localOffset).toInt()
            localOffset += SERIALIZED_UINT_SIZE

            val refs = hashMapOf<ByteArrayKey, Long>()

            for (i in 0 until size) {
                val hash = buffer.copyOfRange(localOffset, localOffset + SERIALIZED_SHA1_HASH_SIZE)
                localOffset += SERIALIZED_SHA1_HASH_SIZE
                val version = deserializeULong(buffer, localOffset).toLong()
                localOffset += SERIALIZED_ULONG_SIZE
                refs[hash.toKey()] = version
            }

            return Pair(RevocationUpdateRequestPayload(refs), localOffset)
        }
    }

}

