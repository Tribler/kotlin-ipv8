package nl.tudelft.ipv8.attestation.wallet.cryptography.bonehexact

import nl.tudelft.ipv8.attestation.common.consts.AlgorithmNames.BONEH_EXACT
import nl.tudelft.ipv8.attestation.wallet.consts.Cryptography.ALGORITHM
import nl.tudelft.ipv8.attestation.wallet.consts.Cryptography.HASH
import nl.tudelft.ipv8.attestation.wallet.consts.Cryptography.KEY_SIZE
import nl.tudelft.ipv8.attestation.wallet.consts.Cryptography.SHA256
import nl.tudelft.ipv8.attestation.wallet.consts.Cryptography.SHA256_4
import nl.tudelft.ipv8.attestation.wallet.consts.Cryptography.SHA512
import nl.tudelft.ipv8.attestation.wallet.cryptography.IdentityAlgorithm
import nl.tudelft.ipv8.attestation.wallet.cryptography.*
import nl.tudelft.ipv8.attestation.wallet.cryptography.bonehexact.attestations.BonehAttestation
import nl.tudelft.ipv8.messaging.*
import java.math.BigInteger

const val ALGORITHM_NAME = BONEH_EXACT
const val MIN_KEY_SIZE = 32
const val MAX_KEY_SIZE = 512

class BonehExact(
    val idFormat: String,
    val formats: HashMap<String, HashMap<String, Any>>
) :
    IdentityAlgorithm(idFormat, formats) {

    private val keySize = formats[idFormat]?.get(KEY_SIZE) as Int
    private var attestationFunction: (BonehPublicKey, ByteArray) -> BonehAttestation
    private var aggregateReference: (ByteArray) -> HashMap<Int, Int>

    init {
        this.honestCheck = true

        if (!formats.containsKey(idFormat)) {
            throw RuntimeException("Identity format $idFormat not found!")
        }

        val format = formats[idFormat]!!

        if (format[ALGORITHM] !== ALGORITHM_NAME) {
            throw RuntimeException("Identity format linked to wrong algorithm!")
        }

        if (this.keySize < MIN_KEY_SIZE || this.keySize > MAX_KEY_SIZE) {
            throw RuntimeException("Illegal key size specified!")
        }

        when (val hashMode = format[HASH]) {
            SHA256 -> {
                this.attestationFunction = ::attestSHA256
                this.aggregateReference = ::binaryRelativitySHA256
            }
            SHA256_4 -> {
                this.attestationFunction = ::attestSHA256_4
                this.aggregateReference = ::binaryRelativitySHA256_4
            }
            SHA512 -> {
                this.attestationFunction = ::attestSHA512
                this.aggregateReference = ::binaryRelativitySHA512
            }
            else -> throw RuntimeException("Unknown hashing mode $hashMode")
        }
    }

    override fun deserialize(serialized: ByteArray, idFormat: String): WalletAttestation {
        return BonehAttestation.deserialize(serialized, idFormat)
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

    override fun certainty(value: ByteArray, aggregate: HashMap<Any, Any>): Double {
        @Suppress("UNCHECKED_CAST")
        return binaryRelativityCertainty(
            this.aggregateReference(value),
            aggregate as HashMap<Int, Int>
        )
    }

    override fun createChallenges(
        publicKey: BonehPublicKey,
        attestation: WalletAttestation
    ): ArrayList<ByteArray> {
        attestation as BonehAttestation
        val challenges = arrayListOf<ByteArray>()
        for (bitPair in attestation.bitPairs) {
            val challenge = createChallenge(attestation.publicKey, bitPair)
            val serialized =
                serializeVarLen(challenge.a.toByteArray()) + serializeVarLen(challenge.b.toByteArray())
            challenges.add(serialized)
        }

        return challenges
    }

    override fun createChallengeResponse(
        privateKey: BonehPrivateKey,
        attestation: WalletAttestation,
        challenge: ByteArray,
    ): ByteArray {
        val (a, b) = deserializeRecursively(challenge)
        val pair = Pair(BigInteger(a), BigInteger(b))
        val response = createChallengeResponseFromPair(privateKey, pair)
        return serializeUInt(response.toUInt())
    }

    override fun processChallengeResponse(
        aggregate: HashMap<Any, Any>,
        challenge: ByteArray?,
        response: ByteArray,
    ) {
        val deserialized = deserializeUInt(response)
        @Suppress("UNCHECKED_CAST")
        return internalProcessChallengeResponse(
            aggregate as HashMap<Int, Int>,
            deserialized.toInt()
        )
    }

    override fun createCertaintyAggregate(attestation: WalletAttestation?): HashMap<Any, Any> {
        @Suppress("UNCHECKED_CAST")
        return createEmptyRelativityMap() as HashMap<Any, Any>
    }

    override fun createHonestyChallenge(publicKey: BonehPublicKey, value: Int): ByteArray {
        val rawChallenge = createHonestyCheck(publicKey, value)
        return serializeVarLen(rawChallenge.a.toByteArray()) + serializeVarLen(rawChallenge.b.toByteArray())
    }

    override fun processHonestyChallenge(value: Int, response: ByteArray): Boolean {
        return value.toUInt() == deserializeUInt(response)
    }
}
