package nl.tudelft.ipv8.attestation.communication.caches

import nl.tudelft.ipv8.attestation.common.RequestCache
import nl.tudelft.ipv8.attestation.communication.DisclosureRequest
import nl.tudelft.ipv8.attestation.wallet.caches.DEFAULT_TIMEOUT
import nl.tudelft.ipv8.attestation.wallet.caches.HashCache
import nl.tudelft.ipv8.attestation.wallet.caches.NumberCache
import java.math.BigInteger

const val DISCLOSURE_REQUEST_PREFIX = "disclosure-request"

class DisclosureRequestCache(
    requestCache: RequestCache,
    id: String,
    val disclosureRequest: DisclosureRequest,
    timeout: Int = DEFAULT_TIMEOUT
) : NumberCache(
    requestCache, DISCLOSURE_REQUEST_PREFIX,
    idFromUUID(id).second, timeout
) {



    companion object {
        fun idFromUUID(id: String): Pair<String, BigInteger> {
            return HashCache.idFromHash(DISCLOSURE_REQUEST_PREFIX, id.toByteArray())
        }
    }
}
