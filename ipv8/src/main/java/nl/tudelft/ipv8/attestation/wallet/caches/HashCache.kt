package nl.tudelft.ipv8.attestation.wallet.caches

import nl.tudelft.ipv8.messaging.deserializeUChar

open class HashCache(prefix: String, cacheHash: ByteArray, idFormat: String) {

    companion object {
        fun idFromHash(prefix: String, cacheHash: ByteArray): Pair<String, Int> {
            // TODO: Verify that this works
            var number = 0
            var offset = 0
            for (i in 0..cacheHash.size) {
                val b = deserializeUChar(cacheHash, offset)
                number = number shl 8
                number = number or b.toInt()
                offset += 1
            }
            return Pair(prefix, number)
        }
    }

}
