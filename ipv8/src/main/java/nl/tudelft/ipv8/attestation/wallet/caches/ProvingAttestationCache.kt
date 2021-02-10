package nl.tudelft.ipv8.attestation.wallet.caches

import nl.tudelft.ipv8.attestation.wallet.AttestationCommunity
import nl.tudelft.ipv8.attestation.wallet.cryptography.bonehexact.BonehPublicKey

const val PROVING_ATTESTATION_PREFIX = "proving-attestation"

class ProvingAttestationCache(
    community: AttestationCommunity,
    val cacheHash: ByteArray,
    idFormat: String,
    val publicKey: BonehPublicKey? = null,
    private val onComplete: (ByteArray, HashMap<Int, Int>) -> Unit = { _: ByteArray, _: HashMap<Int, Int> -> null },
) : HashCache(community.requestCache, PROVING_ATTESTATION_PREFIX, cacheHash, idFormat) {

    var relativityMap: HashMap<Int, Int> = hashMapOf()
    var hashedChallenges: ArrayList<ByteArray> = arrayListOf()
    var challenges: ArrayList<ByteArray> = arrayListOf()

    fun attestationCallbacks(cacheHash: ByteArray, relativityMap: HashMap<Int, Int>) = ::onComplete
}
