package nl.tudelft.ipv8.attestation.wallet.caches

import java.math.BigInteger

class PendingChallengesCache(prefix: String, cacheHash: ByteArray, idFormat: String) :
    HashCache(prefix, cacheHash, idFormat) {

    companion object {
        fun idFromHash(prefix: String, cacheHash: ByteArray): Pair<String, BigInteger> {
            return HashCache.idFromHash(prefix, cacheHash)
        }
    }
}
