package nl.tudelft.ipv8.attestation.wallet.caches

import nl.tudelft.ipv8.attestation.wallet.RequestCache
import java.math.BigInteger

open class PeerCache(val cacheHash: RequestCache, prefix: String, val mid: String, val idFormat: String) :
    NumberCache(cacheHash, prefix, idFromAddress(prefix, mid).second) {

    companion object {
        fun idFromAddress(prefix: String, mid: String): Pair<String, BigInteger> {
            return HashCache.idFromHash(prefix, mid.toByteArray())
        }
    }
}
