package nl.tudelft.ipv8.attestation.wallet.caches

import nl.tudelft.ipv8.attestation.common.RequestCache
import java.math.BigInteger

open class PeerCache(cache: RequestCache, prefix: String, val mid: String, val idFormat: String) :
    NumberCache(cache, prefix, idFromAddress(prefix, mid).second) {

    companion object {
        fun idFromAddress(prefix: String, mid: String): Pair<String, BigInteger> {
            return HashCache.idFromHash(prefix, mid.toByteArray())
        }
    }
}
