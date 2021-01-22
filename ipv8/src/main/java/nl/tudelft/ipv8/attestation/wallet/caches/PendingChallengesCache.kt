package nl.tudelft.ipv8.attestation.wallet.caches

class PendingChallengesCache(prefix: String, cacheHash: ByteArray, idFormat: String) :
    HashCache(prefix, cacheHash, idFormat) {

    companion object {
        fun idFromHash(prefix: String, cacheHash: ByteArray): Pair<String, Int> {
            return HashCache.idFromHash(prefix, cacheHash)
        }
    }
}
