package nl.tudelft.ipv8.attestation.wallet.cryptography.pengbaorange

import nl.tudelft.ipv8.attestation.IdentityAlgorithm
import nl.tudelft.ipv8.attestation.WalletAttestation
import nl.tudelft.ipv8.attestation.wallet.cryptography.bonehexact.BonehPrivateKey
import nl.tudelft.ipv8.attestation.wallet.cryptography.bonehexact.BonehPublicKey
import nl.tudelft.ipv8.attestation.wallet.cryptography.bonehexact.generateKeypair
import nl.tudelft.ipv8.messaging.deserializeBool
import nl.tudelft.ipv8.messaging.deserializeRecursively
import nl.tudelft.ipv8.messaging.serializeVarLen
import nl.tudelft.ipv8.util.ByteArrayKey
import nl.tudelft.ipv8.util.toHex
import java.math.BigInteger
import java.security.SecureRandom

const val LARGE_INTEGER = 32765

private fun safeRandomNumber(keySize: Int, mod: BigInteger): BigInteger {
    val random = SecureRandom()
    val largeBigInteger = LARGE_INTEGER.toBigInteger()
    fun randomNumber(): BigInteger {
        return BigInteger(keySize, random).mod(mod)
    }

    var out = randomNumber()
    while (out < largeBigInteger) {
        out = randomNumber()
    }
    return out
}

class Pengbaorange(idFormat: String, formats: HashMap<String, HashMap<String, Any>>) :
    IdentityAlgorithm(idFormat, formats) {

    private val keySize: Int
    private val a: Int
    private val b: Int

    init {
        if (formats.get(idFormat) == null) {
            throw RuntimeException("Unknown identity format.")
        }
        val format = formats[idFormat]!!
        if (format.get("algorithm") != "pengbaorange") {
            throw RuntimeException("Identity format linked to wrong algorithm.")
        }

        keySize = formats[idFormat]!!.get("key_size") as Int

        if (this.keySize < 32 || this.keySize > 512) {
            throw RuntimeException("Illegal key size specified.")
        }

        a = format.get("min") as Int
        b = format.get("max") as Int
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
        val parsedValue = BigInteger(value.toHex(), 16)
        return createAttestationPair(publicKey, parsedValue, this.a, this.b, this.keySize).serializePrivate(publicKey)
    }

    override fun certainty(value: ByteArray, aggregate: HashMap<Any, Any>): Double {
        var inRange = aggregate.size > 1
        for ((k, v) in aggregate.entries) {
            if (k != "attestation") {
                inRange = inRange && (v as Boolean)
            }
        }
        val match = if (inRange) 1.0 else 0.0
        return if (deserializeBool(value)) match else 1.0 - match
    }

    override fun createChallenges(publicKey: BonehPublicKey, attestation: WalletAttestation): ArrayList<ByteArray> {
        val mod = publicKey.g.mod - BigInteger.ONE
        return arrayListOf(serializeVarLen(safeRandomNumber(this.keySize, mod).toByteArray()) + serializeVarLen(
            safeRandomNumber(this.keySize, mod).toByteArray()))

    }

    override fun createChallengeResponse(
        privateKey: BonehPrivateKey,
        attestation: WalletAttestation,
        challenge: ByteArray,
    ): ByteArray {
        val (s, t) = deserializeRecursively(challenge).map(::BigInteger)

        if (s < LARGE_INTEGER.toBigInteger() || t < LARGE_INTEGER.toBigInteger()) {
            return (
                serializeVarLen(safeRandomNumber(this.keySize, privateKey.g.mod).toByteArray())
                    + safeRandomNumber(this.keySize, privateKey.g.mod).toByteArray()
                    + safeRandomNumber(this.keySize, privateKey.g.mod).toByteArray()
                    + safeRandomNumber(this.keySize, privateKey.g.mod).toByteArray()
                )
        }
        val (x, y, u, v) = (attestation as PengBaoAttestation).privateData!!.generateResponse(s, t)

        return (
            serializeVarLen(x.toByteArray()) + serializeVarLen(y.toByteArray())
                + serializeVarLen(u.toByteArray()) + serializeVarLen(v.toByteArray())
            )
    }

    override fun createCertaintyAggregate(attestation: WalletAttestation?): HashMap<Any, Any> {
        @Suppress("UNCHECKED_CAST")
        return hashMapOf("attestation" to (attestation as PengBaoAttestation)) as HashMap<Any, Any>
    }


    override fun processChallengeResponse(
        aggregate: HashMap<Any, Any>,
        challenge: ByteArray?,
        response: ByteArray,
    ): HashMap<Any, Any> {
        val (x, y, u, v) = deserializeRecursively(response).map(::BigInteger)
        val (s, t) = deserializeRecursively(challenge!!).map(::BigInteger)

        val attestation = aggregate["attestation"] as PengBaoAttestation
        aggregate[ByteArrayKey(challenge)] = attestation.publicData.check(this.a, this.b, s, t, x, y, u, v)
        return aggregate
    }


    override fun createHonestyChallenge(publicKey: BonehPublicKey, value: Int): ByteArray {
        throw NotImplementedError("This method has not been implemented for this algorithm.")
    }

    override fun processHonestyChallenge(value: Int, response: ByteArray): Boolean {
        throw NotImplementedError("This method has not been implemented for this algorithm.")
    }

    override fun deserialize(serialized: ByteArray, idFormat: String): WalletAttestation {
        return PengBaoAttestation.deserialize(serialized, idFormat)
    }

}
