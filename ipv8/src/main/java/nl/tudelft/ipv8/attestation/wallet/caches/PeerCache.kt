package nl.tudelft.ipv8.attestation.wallet.caches

import nl.tudelft.ipv8.caches.NumberCache
import nl.tudelft.ipv8.caches.RequestCache

class PeerCache(cacheHash: RequestCache, prefix: String, idFormat: String) :
    NumberCache(cacheHash, prefix, idFromAddress(prefix, idFormat).second) {

    companion object {
        fun idFromAddress(prefix: String, mid: String): Pair<String, Int> {
            return HashCache.idFromHash(prefix, mid.toByteArray(Charsets.UTF_8))
        }
    }
}
