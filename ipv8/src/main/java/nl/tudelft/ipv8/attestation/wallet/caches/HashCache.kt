package nl.tudelft.ipv8.attestation.wallet.caches

import nl.tudelft.ipv8.messaging.deserializeUChar
import java.math.BigInteger

open class HashCache(prefix: String, cacheHash: ByteArray, idFormat: String) {

    companion object {
        fun idFromHash(prefix: String, cacheHash: ByteArray): Pair<String, BigInteger> {
            var number = BigInteger.ZERO
            for (i in cacheHash.indices) {
                val b = deserializeUChar(cacheHash.copyOfRange(i, i + 1))
                number = number shl 8
                number = number or BigInteger(b.toString())
            }
            return Pair(prefix, number)
        }
    }

}
