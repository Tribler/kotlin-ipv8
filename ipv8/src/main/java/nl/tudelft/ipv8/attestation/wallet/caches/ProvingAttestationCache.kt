package nl.tudelft.ipv8.attestation.wallet.caches

import nl.tudelft.ipv8.attestation.wallet.AttestationCommunity
import nl.tudelft.ipv8.attestation.wallet.cryptography.bonehexact.BonehPublicKey

const val PROVING_ATTESTATION_PREFIX = "proving-attestation"

class ProvingAttestationCache(
    community: AttestationCommunity,
    val cacheHash: ByteArray,
    idFormat: String,
    var publicKey: BonehPublicKey? = null,
    private val onComplete: (ByteArray, HashMap<Any, Any>) -> Unit = { _: ByteArray, _: HashMap<Any, Any> -> },
) : HashCache(community.requestCache, PROVING_ATTESTATION_PREFIX, cacheHash, idFormat) {

    var relativityMap: HashMap<Any, Any> = hashMapOf()
    var hashedChallenges: ArrayList<ByteArray> = arrayListOf()
    var challenges: ArrayList<ByteArray> = arrayListOf()

    fun attestationCallbacks(cacheHash: ByteArray, relativityMap: HashMap<Any, Any>) =
        onComplete(cacheHash, relativityMap)
}
