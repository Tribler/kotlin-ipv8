package nl.tudelft.ipv8.attestation.revocation.caches

import nl.tudelft.ipv8.Peer
import nl.tudelft.ipv8.attestation.common.RequestCache
import nl.tudelft.ipv8.attestation.wallet.caches.HashCache
import nl.tudelft.ipv8.attestation.wallet.caches.NumberCache
import java.math.BigInteger

const val ALLOWED_REVOCATION_UPDATE_REQUEST_CACHE_PREFIX = "allowed-update-request"

class AllowedRevocationUpdateRequestCache(
    requestCache: RequestCache,
    peer: Peer
) :
    NumberCache(requestCache, ALLOWED_REVOCATION_UPDATE_REQUEST_CACHE_PREFIX, this.generateId(peer).second, timeout = 30) {

    companion object {
        fun generateId(peer: Peer): Pair<String, BigInteger> {
            return HashCache.idFromHash(ALLOWED_REVOCATION_UPDATE_REQUEST_CACHE_PREFIX, peer.publicKey.keyToHash())
        }
    }
}
