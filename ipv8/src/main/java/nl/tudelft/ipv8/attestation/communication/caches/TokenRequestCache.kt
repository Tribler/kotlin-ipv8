package nl.tudelft.ipv8.attestation.communication.caches

import nl.tudelft.ipv8.attestation.common.RequestCache
import nl.tudelft.ipv8.attestation.wallet.caches.HashCache
import nl.tudelft.ipv8.attestation.wallet.caches.NumberCache
import java.math.BigInteger

const val TOKEN_REQUEST_CACHE = "token-request"

// TODO: add second parameter for uniqueness.
class TokenRequestCache(cache: RequestCache, mid: String, val requestedAttributeName: String, val disclosureInformation: String) :
    NumberCache(cache, TOKEN_REQUEST_CACHE, generateId(mid).second) {

    companion object {
        fun generateId(mid: String): Pair<String, BigInteger> {
            return HashCache.idFromHash(TOKEN_REQUEST_CACHE, mid.toByteArray())
        }
    }
}
