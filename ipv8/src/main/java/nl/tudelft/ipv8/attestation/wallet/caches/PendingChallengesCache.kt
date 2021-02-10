package nl.tudelft.ipv8.attestation.wallet.caches

import nl.tudelft.ipv8.attestation.wallet.AttestationCommunity
import java.math.BigInteger

const val PENDING_CHALLENGES_PREFIX = "proving-hash"

class PendingChallengesCache(
    community: AttestationCommunity,
    val cacheHash: ByteArray,
    val provingCache: ProvingAttestationCache,
    idFormat: String,
    val honestyCheck: Int = -1,
) :
    HashCache(community.requestCache, PENDING_CHALLENGES_PREFIX, cacheHash, idFormat)
