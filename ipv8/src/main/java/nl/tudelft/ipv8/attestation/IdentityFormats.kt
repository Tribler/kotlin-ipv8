package nl.tudelft.ipv8.attestation

import nl.tudelft.ipv8.attestation.wallet.cryptography.bonehexact.BonehPrivateKey
import nl.tudelft.ipv8.attestation.wallet.cryptography.bonehexact.BonehPublicKey
import nl.tudelft.ipv8.util.sha1
import java.math.BigDecimal

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

// TODO: Create WalletAttestationKey superclass
abstract class WalletAttestation {

    abstract val publicKey: BonehPublicKey
    abstract val idFormat: String?

    abstract fun serialize(): ByteArray

    abstract fun deserialize(
        serialized: ByteArray,
        idFormat: String,
    ): WalletAttestation

    abstract fun serializePrivate(publicKey: BonehPublicKey): ByteArray

    abstract fun deserializePrivate(
        privateKey: BonehPrivateKey,
        serialized: ByteArray,
        idFormat: String?,
    ): WalletAttestation

    fun getHash(): ByteArray {
        return sha1(this.serialize())
    }

    override fun toString(): String {
        return "WalletAttestation(publicKey=$publicKey, idFormat=$idFormat)"
    }

}
