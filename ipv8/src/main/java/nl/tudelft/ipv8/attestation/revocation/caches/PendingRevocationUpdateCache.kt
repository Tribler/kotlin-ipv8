package nl.tudelft.ipv8.attestation.revocation.caches

import nl.tudelft.ipv8.attestation.wallet.RequestCache
import nl.tudelft.ipv8.attestation.wallet.caches.HashCache
import nl.tudelft.ipv8.attestation.wallet.caches.NumberCache
import nl.tudelft.ipv8.attestation.wallet.caches.PeerCache

const val PENDING_REVOCATION_UPDATE_CACHE_PREFIX = "receive-revocation"

class PendingRevocationUpdateCache(requestCache: RequestCache, peerMID: String) :
    NumberCache(requestCache, PENDING_REVOCATION_UPDATE_CACHE_PREFIX, PeerCache.idFromAddress(
        PENDING_REVOCATION_UPDATE_CACHE_PREFIX, peerMID).second) {

        val revocationMap = hashMapOf<Int, ByteArray>()
}
