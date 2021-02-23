package nl.tudelft.ipv8.attestation.wallet.caches

import nl.tudelft.ipv8.attestation.wallet.AttestationCommunity
import nl.tudelft.ipv8.attestation.wallet.cryptography.bonehexact.BonehPrivateKey

const val ATTESTATION_REQUEST_PREFIX = "receive-request-attestation"

class ReceiveAttestationRequestCache(
    val community: AttestationCommunity,
    mid: String,
    val privateKey: BonehPrivateKey,
    val name: String,
    idFormat: String,
    val signature: Boolean = false,
) : PeerCache(community.requestCache, ATTESTATION_REQUEST_PREFIX, mid, idFormat) {

    val attestationMap: MutableSet<Pair<Int, ByteArray>> = mutableSetOf()

}
