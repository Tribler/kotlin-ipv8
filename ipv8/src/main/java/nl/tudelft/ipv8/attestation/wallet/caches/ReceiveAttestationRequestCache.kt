package nl.tudelft.ipv8.attestation.wallet.caches

import nl.tudelft.ipv8.attestation.wallet.AttestationCommunity
import nl.tudelft.ipv8.keyvault.PrivateKey

const val PREFIX = "receive-verify-attestation".toByteArray()

class ReceiveAttestationRequestCache(
    val community: AttestationCommunity,
    val mid: String,
    val privateKey: PrivateKey,
    val name: String,
    val idFormat: String) : PeerCache(community.attestationRequestCache, PREFIX, idFormat)
) {
}
