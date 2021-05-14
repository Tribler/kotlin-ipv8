package nl.tudelft.ipv8.attestation.wallet.cryptography

import nl.tudelft.ipv8.attestation.wallet.cryptography.bonehexact.BonehPrivateKey
import nl.tudelft.ipv8.attestation.wallet.cryptography.bonehexact.BonehPublicKey

abstract class IdentityAlgorithm(idFormat: String, formats: HashMap<String, HashMap<String, Any>>) {
    var honestCheck = false

    init {
        val containsAlgorithm = formats.containsKey(idFormat)
        if (!containsAlgorithm) {
            throw RuntimeException("Attempted to initialize with illegal identity format $idFormat!")
        }
    }

    abstract fun deserialize(
        serialized: ByteArray,
        idFormat: String,
    ): WalletAttestation

    abstract fun generateSecretKey(): BonehPrivateKey

    abstract fun loadSecretKey(serializedKey: ByteArray): BonehPrivateKey

    abstract fun loadPublicKey(serializedKey: ByteArray): BonehPublicKey

    abstract fun attest(publicKey: BonehPublicKey, value: ByteArray): ByteArray

    // Any type as aggregates can contain numerous types.
    abstract fun certainty(value: ByteArray, aggregate: HashMap<Any, Any>): Double

    abstract fun createChallenges(publicKey: BonehPublicKey, attestation: WalletAttestation): ArrayList<ByteArray>

    abstract fun createChallengeResponse(
        privateKey: BonehPrivateKey,
        attestation: WalletAttestation,
        challenge: ByteArray,
    ): ByteArray

    abstract fun processChallengeResponse(
        aggregate: HashMap<Any, Any>,
        challenge: ByteArray?,
        response: ByteArray,
    ): Any

    abstract fun createCertaintyAggregate(attestation: WalletAttestation?): HashMap<Any, Any>

    abstract fun createHonestyChallenge(publicKey: BonehPublicKey, value: Int): ByteArray

    abstract fun processHonestyChallenge(value: Int, response: ByteArray): Boolean

}
