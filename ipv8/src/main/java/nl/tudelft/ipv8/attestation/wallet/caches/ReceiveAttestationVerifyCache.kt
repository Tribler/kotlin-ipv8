package nl.tudelft.ipv8.attestation.wallet.caches

import nl.tudelft.ipv8.attestation.wallet.AttestationCommunity

const val ATTESTATION_VERIFY_PREFIX = "receive-verify-attestation"

class ReceiveAttestationVerifyCache(community: AttestationCommunity, cacheHash: ByteArray, idFormat: String) :
    HashCache(community.requestCache, ATTESTATION_VERIFY_PREFIX, cacheHash, idFormat) {

    val attestationMap: MutableSet<Pair<Int, ByteArray>> = mutableSetOf()

}
