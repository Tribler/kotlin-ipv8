package nl.tudelft.ipv8.attestation.revocation.datastructures

import java.math.BigInteger
import java.security.MessageDigest
import java.util.BitSet
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

/**
 * Adapted from https://github.com/ackintosh/bloom-filter-kotlin.
 */
class BloomFilter(initialCapacity: Int) {
    private val bits = BitSet(initialCapacity)
    private val hash = Hash(initialCapacity)
    private val lock = ReentrantReadWriteLock()

    fun add(value: ByteArray) {
        lock.write {
            for (seed in 1..3) {
                bits.set(hash.call(seed, value))
            }
        }
    }

    fun probablyContains(value: ByteArray): Boolean {
        lock.read {
            for (seed in 1..3) {
                if (!bits.get(hash.call(seed, value))) {
                    return false
                }
            }
        }
        return true
    }

    private class Hash(val filterSize: Int) {
        private val md5 = MessageDigest.getInstance("MD5")

        fun call(seed: Int, value: ByteArray) =
            BigInteger(1, md5.digest(value + seed.toByte()))
                .remainder(BigInteger.valueOf(filterSize.toLong()))
                .toInt()
    }
}
