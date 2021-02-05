package nl.tudelft.ipv8.attestation.wallet.cryptography.bonehexact

import nl.tudelft.ipv8.attestation.IdentityAlgorithm
import nl.tudelft.ipv8.attestation.WalletAttestation
import nl.tudelft.ipv8.attestation.wallet.cryptography.bonehexact.attestations.BonehAttestation
import nl.tudelft.ipv8.keyvault.PrivateKey
import nl.tudelft.ipv8.keyvault.PublicKey
import nl.tudelft.ipv8.messaging.*
import java.math.BigInteger

const val ALGORITHM_NAME = "bonehexact"

class BonehExactAlgorithm(val idFormat: String, val formats: HashMap<String, HashMap<String, Any>>) :
    IdentityAlgorithm(idFormat, formats) {

    val keySize = formats[idFormat]?.get("key_size") as Int
    val attestationClass = BonehAttestation
    lateinit var attestationFunction: (BonehPublicKey, ByteArray) -> BonehAttestation
    lateinit var aggregateReference: (ByteArray) -> HashMap<Int, Int>

    init {
        this.honestCheck = true

        if (formats.containsKey(idFormat)) {
            throw RuntimeException("Identity format $idFormat not found!")
        }

        val format = formats[idFormat]!!

        if (format.get("algorithm") !== ALGORITHM_NAME) {
            throw RuntimeException("Identity format linked to wrong algorithm!")
        }

        if (this.keySize < 32 || this.keySize > 512) {
            throw RuntimeException("Illegal key size specified!")
        }

        val hashMode = format.get("hash")

        if (hashMode == "sha256") {
            this.attestationFunction = ::attestSHA256
            this.aggregateReference = ::binaryRelativitySHA256
        }
        // TODO add other hash functions.

    }

    override fun deserialize(serialized: ByteArray, idFormat: String): WalletAttestation {
        return this.attestationClass.deserialize(serialized, idFormat)
    }

    override fun generateSecretKey(): BonehPrivateKey {
        return generateKeypair(this.keySize).second
    }

    override fun loadSecretKey(serializedKey: ByteArray): BonehPrivateKey {
        return BonehPrivateKey.deserialize(serializedKey)!!
    }

    override fun loadPublicKey(serializedKey: ByteArray): BonehPublicKey {
        return BonehPublicKey.deserialize(serializedKey)!!
    }

    override fun attest(publicKey: BonehPublicKey, value: ByteArray): ByteArray {
        return this.attestationFunction(publicKey, value).serialize()
    }

    override fun certainty(value: ByteArray, aggregate: HashMap<Int, Int>): Float {
        return binaryRelativityCertainty(this.aggregateReference(value), aggregate)
    }

    override fun createChallenges(publicKey: BonehPublicKey, attestation: WalletAttestation): ArrayList<ByteArray> {
        val attestation = attestation as BonehAttestation
        val challenges = arrayListOf<ByteArray>()
        for (bitpair in attestation.bitpairs) {
            val challenge = createChallenge(attestation.publicKey, bitpair)
            val serialized = serializeVarLen(challenge.a.toByteArray()) + serializeVarLen(challenge.b.toByteArray())
            challenges.add(serialized)
        }

        return challenges
    }

    override fun createChallengeResponse(
        privateKey: BonehPrivateKey,
        attestation: WalletAttestation,
        challenge: ByteArray,
    ): ByteArray {
        val deserialized = deserializeRecursively(challenge)
        val pair = Pair(BigInteger(deserialized[0]), BigInteger(deserialized[1]))
        return serializeUInt(createChallengeResponseFromPair(privateKey, pair).toUInt())
    }

    override fun processChallengeResponse(
        aggregate: HashMap<Int, Int>,
        challenge: ByteArray?,
        response: ByteArray,
    ) {
        val deserialized = deserializeUInt(response)
        return internalProcessChallengeResponse(aggregate, deserialized.toInt())
    }

    override fun createCertaintyAggregate(attestation: WalletAttestation?): HashMap<Int, Int> {
        return createEmptyRelativityMap()
    }

    override fun createHonestyChallenge(publicKey: BonehPublicKey, value: Int): ByteArray {
        val rawChallenge = createHonestyCheck(publicKey, value)
        return serializeVarLen(rawChallenge.a.toByteArray()) + serializeVarLen(rawChallenge.b.toByteArray())
    }

    override fun processHonestyChallenge(value: Int, response: ByteArray): Boolean {
        return value.toUInt() == deserializeUInt(response)
    }
}
