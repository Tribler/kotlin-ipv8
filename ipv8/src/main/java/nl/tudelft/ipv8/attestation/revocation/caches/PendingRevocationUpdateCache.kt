package nl.tudelft.ipv8.attestation.revocation.caches

import nl.tudelft.ipv8.attestation.RequestCache
import nl.tudelft.ipv8.attestation.wallet.caches.HashCache
import nl.tudelft.ipv8.attestation.wallet.caches.NumberCache
import nl.tudelft.ipv8.util.sha1
import nl.tudelft.ipv8.util.toByteArray
import java.math.BigInteger

const val PENDING_REVOCATION_UPDATE_CACHE_PREFIX = "receive-revocation"

class PendingRevocationUpdateCache(
    requestCache: RequestCache,
    senderHash: ByteArray,
    signeeHash: ByteArray,
    version: Long,
        override val timeout: Int = 10,
) :
    NumberCache(requestCache, PENDING_REVOCATION_UPDATE_CACHE_PREFIX, this.generateId(
        senderHash, signeeHash, version).second) {

    val revocationMap = hashMapOf<Int, ByteArray>()

    companion object {
        fun generateId(senderHash: ByteArray, signeeHash: ByteArray, version: Long): Pair<String, BigInteger> {
            val hash = sha1(senderHash + signeeHash + version.toByteArray())
            return HashCache.idFromHash(PENDING_REVOCATION_UPDATE_CACHE_PREFIX, hash)
        }
    }
}
