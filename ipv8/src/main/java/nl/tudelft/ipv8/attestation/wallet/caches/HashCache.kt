package nl.tudelft.ipv8.attestation.wallet.caches

import nl.tudelft.ipv8.attestation.wallet.RequestCache
import nl.tudelft.ipv8.messaging.deserializeUChar
import java.math.BigInteger

open class HashCache(requestCache: RequestCache, prefix: String, cacheHash: ByteArray, val idFormat: String) :
    NumberCache(requestCache, prefix, idFromHash(prefix, cacheHash).second) {

    companion object {
        fun idFromHash(prefix: String, cacheHash: ByteArray): Pair<String, BigInteger> {
            var number = BigInteger.ZERO
            for (i in cacheHash.indices) {
                // TODO: Verify whether we can simply invoke cacheHash[i].toUByte().
                val b = deserializeUChar(cacheHash.copyOfRange(i, i + 1))
                number = number shl 8
                number = number or BigInteger(b.toString())
            }
            return Pair(prefix, number)
        }

    }

}
