package nl.tudelft.ipv8.attestation.attestation

import nl.tudelft.ipv8.attestation.wallet.bonehexact.ALGORITHM_NAME
import nl.tudelft.ipv8.keyvault.PrivateKey
import nl.tudelft.ipv8.keyvault.PublicKey
import nl.tudelft.ipv8.util.sha1

abstract class IdentityAlgorithm(private val idFormat: String, formats: HashMap<String, HashMap<String, Any>>) {
    var honestCheck = false;

    init {
        var containsAlgorithm = false
        for (map in formats.values) {
            if (map["algorithm"] == ALGORITHM_NAME) {
                containsAlgorithm = true
            }
        }
        if (!containsAlgorithm) {
            throw RuntimeException("Attempted to initialize with illegal identity format!")
        }
    }

    abstract fun generateSecretKey(): PrivateKey

    abstract fun loadSecretKey(serializedKey: ByteArray): PrivateKey

    abstract fun loadPublicKey(serializedKey: ByteArray): PublicKey

    abstract fun getAttestationClass(): Class<WalletAttestation>

    abstract fun attest(publicKey: PublicKey, value: String): ByteArray

    abstract fun certainty(value: Int, aggregate: Map<String, WalletAttestation>): Float

    abstract fun createChallenges(publicKey: PublicKey, attestation: WalletAttestation): ArrayList<ByteArray>

    abstract fun createChallengeResponse(
        privateKey: PrivateKey,
        attestation: WalletAttestation,
        challenge: ByteArray,
    ): ByteArray

    abstract fun processChallengeResponse(
        aggregate: MutableMap<String, WalletAttestation>,
        challenge: ByteArray?,
        response: ByteArray,
    ): String

    abstract fun createCertaintyAggregate(attestation: WalletAttestation?): MutableMap<String, WalletAttestation>

    abstract fun createHonestyChallenge(publicKey: PublicKey, value: Int): ByteArray

    abstract fun processHonestyChallenge(value: Int, response: ByteArray): Boolean


}

abstract class WalletAttestation {

    abstract val idFormat: String
    abstract val publicKey: PublicKey
    abstract val relativityMap: HashMap<Int, WalletAttestation>

    abstract fun serialize(): ByteArray
    abstract fun deserialize(
        string: ByteArray,
        idFormat: String,
    ): WalletAttestation

    abstract fun serializePrivate(): ByteArray
    abstract fun deserializePrivate(
        privateKey: PrivateKey,
        string: ByteArray,
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
            privateKey: PrivateKey,
            string: ByteArray,
            idFormat: String,
        ): WalletAttestation {
            throw NotImplementedError()
        }
    }

}
