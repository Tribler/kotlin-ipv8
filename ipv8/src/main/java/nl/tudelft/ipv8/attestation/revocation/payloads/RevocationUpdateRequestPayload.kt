package nl.tudelft.ipv8.attestation.revocation.payloads

import nl.tudelft.ipv8.keyvault.PublicKey
import nl.tudelft.ipv8.keyvault.defaultCryptoProvider
import nl.tudelft.ipv8.messaging.*
import nl.tudelft.ipv8.util.ByteArrayKey
import nl.tudelft.ipv8.util.hexToBytes
import nl.tudelft.ipv8.util.toHex
import org.json.JSONObject

class RevocationUpdateRequestPayload(
    revocationRefs: Map<ByteArrayKey, Long>
) : RevocationUpdatePreviewPayload(revocationRefs) {

    companion object Deserializer : Deserializable<RevocationUpdateRequestPayload> {
        override fun deserialize(buffer: ByteArray, offset: Int): Pair<RevocationUpdateRequestPayload, Int> {
            @Suppress("UNCHECKED_CAST")
            return RevocationUpdatePreviewPayload.deserialize(buffer, offset) as Pair<RevocationUpdateRequestPayload, Int>
        }
    }

}

