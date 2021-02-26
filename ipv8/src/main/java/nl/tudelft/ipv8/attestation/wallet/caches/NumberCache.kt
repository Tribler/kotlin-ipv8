package nl.tudelft.ipv8.attestation.wallet.caches

import nl.tudelft.ipv8.attestation.wallet.RequestCache
import java.math.BigInteger

abstract class NumberCache(val requestCache: RequestCache, val prefix: String, val number: BigInteger) {

    init {
        if (requestCache.has(prefix, number)) {
            throw RuntimeException("Number $number is already in use.")
        }
    }

    // TODO: implement futures.

}
