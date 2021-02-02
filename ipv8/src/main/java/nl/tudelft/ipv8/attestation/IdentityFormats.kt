package nl.tudelft.ipv8.attestation

import nl.tudelft.ipv8.keyvault.PrivateKey
import nl.tudelft.ipv8.keyvault.PublicKey
import nl.tudelft.ipv8.util.sha1

abstract class IdentityAlgorithm(private val idFormat: String, formats: HashMap<String, HashMap<String, Any>>) {
    var honestCheck = false;

    init {
        var containsAlgorithm = false
        for (map in formats.values) {
            if (map["algorithm"] == idFormat) {
                containsAlgorithm = true
            }
        }
        if (!containsAlgorithm) {
            throw RuntimeException("Attempted to initialize with illegal identity format!")
        }
    }

    abstract fun deserialize(
        string: ByteArray,
        idFormat: String,
    ): WalletAttestation

    abstract fun generateSecretKey(): PrivateKey

    abstract fun loadSecretKey(serializedKey: ByteArray): PrivateKey

    abstract fun loadPublicKey(serializedKey: ByteArray): PublicKey

    abstract fun attest(publicKey: PublicKey, value: ByteArray): ByteArray

    abstract fun certainty(value: ByteArray, aggregate: HashMap<Int, Int>): Float

    abstract fun createChallenges(publicKey: PublicKey, attestation: WalletAttestation): ArrayList<ByteArray>

    abstract fun createChallengeResponse(
        privateKey: PrivateKey,
        attestation: WalletAttestation,
        challenge: ByteArray,
    ): ByteArray

    abstract fun processChallengeResponse(
        aggregate: HashMap<Int, Int>,
        challenge: ByteArray?,
        response: ByteArray,
    ): Unit

    abstract fun createCertaintyAggregate(attestation: WalletAttestation?): HashMap<Int, Int>

    abstract fun createHonestyChallenge(publicKey: PublicKey, value: Int): ByteArray

    abstract fun processHonestyChallenge(value: Int, response: ByteArray): Boolean

}

abstract class WalletAttestation {

//    abstract val idFormat: String
//    abstract val publicKey: PublicKey
//    abstract val relativityMap: HashMap<Int, WalletAttestation>

    abstract val publicKey: PublicKey
    abstract val idFormat: String?

    abstract fun serialize(): ByteArray

    abstract fun deserialize(
        string: ByteArray,
        idFormat: String,
    ): WalletAttestation

    abstract fun serializePrivate(publicKey: PublicKey): ByteArray

    abstract fun deserializePrivate(
        privateKey: PrivateKey,
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
            privateKey: PrivateKey,
            string: ByteArray,
            idFormat: String,
        ): WalletAttestation {
            throw NotImplementedError()
        }
    }

}
