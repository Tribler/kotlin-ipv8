package nl.tudelft.ipv8.attestation

import nl.tudelft.ipv8.attestation.wallet.cryptography.bonehexact.BonehPrivateKey
import nl.tudelft.ipv8.attestation.wallet.cryptography.bonehexact.BonehPublicKey
import nl.tudelft.ipv8.keyvault.PrivateKey
import nl.tudelft.ipv8.keyvault.PublicKey
import nl.tudelft.ipv8.util.sha1

abstract class IdentityAlgorithm(idFormat: String, formats: HashMap<String, HashMap<String, Any>>) {
    var honestCheck = false;

    init {
        var containsAlgorithm = formats.containsKey(idFormat)
        if (!containsAlgorithm) {
            throw RuntimeException("Attempted to initialize with illegal identity format $idFormat!")
        }
    }

    abstract fun deserialize(
        string: ByteArray,
        idFormat: String,
    ): WalletAttestation

    abstract fun generateSecretKey(): BonehPrivateKey

    abstract fun loadSecretKey(serializedKey: ByteArray): BonehPrivateKey

    abstract fun loadPublicKey(serializedKey: ByteArray): BonehPublicKey

    abstract fun attest(publicKey: BonehPublicKey, value: ByteArray): ByteArray

    abstract fun certainty(value: ByteArray, aggregate: HashMap<Int, Int>): Float

    abstract fun createChallenges(publicKey: BonehPublicKey, attestation: WalletAttestation): ArrayList<ByteArray>

    abstract fun createChallengeResponse(
        privateKey: BonehPrivateKey,
        attestation: WalletAttestation,
        challenge: ByteArray,
    ): ByteArray

    abstract fun processChallengeResponse(
        aggregate: HashMap<Int, Int>,
        challenge: ByteArray?,
        response: ByteArray,
    ): Unit

    abstract fun createCertaintyAggregate(attestation: WalletAttestation?): HashMap<Int, Int>

    abstract fun createHonestyChallenge(publicKey: BonehPublicKey, value: Int): ByteArray

    abstract fun processHonestyChallenge(value: Int, response: ByteArray): Boolean

}

// TODO: Create WalletAttestationKey superclass
abstract class WalletAttestation {

//    abstract val idFormat: String
//    abstract val publicKey: PublicKey
//    abstract val relativityMap: HashMap<Int, WalletAttestation>

    abstract val publicKey: BonehPublicKey
    abstract val idFormat: String?

    abstract fun serialize(): ByteArray

    abstract fun deserialize(
        string: ByteArray,
        idFormat: String,
    ): WalletAttestation

    abstract fun serializePrivate(publicKey: BonehPublicKey): ByteArray

    abstract fun deserializePrivate(
        privateKey: BonehPrivateKey,
        serialized: ByteArray,
        idFormat: String,
    ): WalletAttestation

    fun getHash(): ByteArray {
        return sha1(this.serialize())
    }

    companion object {
        fun deserialize(
            string: ByteArray,
            idFormat: String,
        ): WalletAttestation {
            throw NotImplementedError()
        }

        fun deserializePrivate(
            privateKey: BonehPrivateKey,
            string: ByteArray,
            idFormat: String,
        ): WalletAttestation {
            throw NotImplementedError()
        }
    }

}
