package nl.tudelft.ipv8.attestation.attestation

import nl.tudelft.ipv8.attestation.identity.Attestation
import nl.tudelft.ipv8.keyvault.PrivateKey
import nl.tudelft.ipv8.keyvault.PublicKey
import nl.tudelft.ipv8.util.sha1

abstract class IdentityAlgorithm(private val idFormat: String, formats: Array<String>) {
    private val honestCheck = false

    init {
        if (!formats.contains(idFormat)) {
            throw RuntimeException("Attempted to initialize with illegal identity format.")
        }
    }

    abstract fun generateSecretKey(): PrivateKey

    abstract fun loadSecretKey(serializedKey: String): PrivateKey

    abstract fun loadPublicKey(serializedKey: String): PublicKey

    abstract fun getAttestationClass(): Attestation

    abstract fun attest(publicKey: PublicKey, value: String): String

    abstract fun certainty(value: String, aggregate: String): Float

    abstract fun createChallenges(publicKey: PublicKey, attestation: Attestation): Array<String>

    abstract fun createChallengeResponse(
        privateKey: PrivateKey,
        attestation: Attestation,
        challenge: String
    ): String

    abstract fun createCertaintyAggregate(attestation: Attestation): String

    abstract fun createHonestyChallenge(publicKey: PublicKey, value: String): String

    abstract fun processChallengeResponse(
        aggregate: String,
        challenge: String,
        response: String
    ): String


}

abstract class Attestation {

    abstract fun serialize(): String

    abstract fun serializePrivate(publicKey: PublicKey): String

    fun getHasH(): ByteArray {
        return sha1(this.serialize().toByteArray())
    }

    companion object {
        fun deserialize(
            string: String,
            idFormat: String
        ): nl.tudelft.ipv8.attestation.attestation.Attestation {
            throw NotImplementedError()
        }

        fun deserializePrivate(
            privateKey: PrivateKey,
            string: String,
            idFormat: String
        ): nl.tudelft.ipv8.attestation.attestation.Attestation {
            throw NotImplementedError()
        }
    }

}
