package nl.tudelft.ipv8.attestation.common

import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import mu.KotlinLogging
import nl.tudelft.ipv8.attestation.wallet.caches.NumberCache
import java.math.BigInteger

class RequestCache {

    private val logger = KotlinLogging.logger {}
    private val lock = Object()
    private val identifiers = hashMapOf<String, NumberCache>()

    fun add(cache: NumberCache): NumberCache? {

        synchronized(lock) {
            val identifier = this.createIdentifier(cache.prefix, cache.number)

            return if (identifiers.containsKey(identifier)) {
                this.logger.error("Attempted to add cache with duplicate identifier $identifier.")
                null
            } else {
                this.logger.debug("Add cache $cache")
                this.identifiers[identifier] = cache
                GlobalScope.launch { cache.start(calleeCallback = { pop(cache.prefix, cache.number) }) }
                cache
            }
        }
    }

    fun has(prefix: String, number: BigInteger): Boolean {
        return this.identifiers.containsKey(this.createIdentifier(prefix, number))
    }

    fun has(identifierPair: Pair<String, BigInteger>): Boolean {
        return this.has(identifierPair.first, identifierPair.second)
    }

    fun pop(identifierPair: Pair<String, BigInteger>): NumberCache? {
        return this.pop(identifierPair.first, identifierPair.second)
    }

    fun pop(prefix: String, number: BigInteger): NumberCache? {
        val identifier = this.createIdentifier(prefix, number)
        val cache = this.identifiers.remove(identifier)
        cache?.stop()
        return cache
    }

    fun get(prefix: String, number: BigInteger): NumberCache? {
        return this.identifiers.get(this.createIdentifier(prefix, number))
    }

    fun get(identifierPair: Pair<String, BigInteger>): NumberCache? {
        return this.get(identifierPair.first, identifierPair.second)
    }

    private fun createIdentifier(prefix: String, number: BigInteger): String {
        return "$prefix:$number"
    }

}
