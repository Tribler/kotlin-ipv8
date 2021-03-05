package nl.tudelft.ipv8.attestation.wallet.cryptography

import nl.tudelft.ipv8.attestation.wallet.cryptography.bonehexact.BonehPrivateKey
import nl.tudelft.ipv8.attestation.wallet.cryptography.bonehexact.BonehPublicKey
import nl.tudelft.ipv8.attestation.wallet.cryptography.bonehexact.attestations.BitPairAttestation
import nl.tudelft.ipv8.attestation.wallet.cryptography.bonehexact.attestations.BonehAttestation
import nl.tudelft.ipv8.attestation.wallet.cryptography.bonehexact.decode
import nl.tudelft.ipv8.attestation.wallet.cryptography.bonehexact.encode
import nl.tudelft.ipv8.attestation.wallet.primitives.FP2Value
import nl.tudelft.ipv8.util.sha256AsBigInt
import nl.tudelft.ipv8.util.sha256_4_AsBigInt
import nl.tudelft.ipv8.util.sha512AsBigInt
import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode
import java.security.SecureRandom
import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.HashMap
import kotlin.math.pow

val lock = Object()

fun attestSHA256(publicKey: BonehPublicKey, value: ByteArray): BonehAttestation {
    return attest(publicKey, sha256AsBigInt(value), 256)
}

fun attestSHA256_4(publicKey: BonehPublicKey, value: ByteArray): BonehAttestation {
    return attest(publicKey, sha256_4_AsBigInt(value), 32)
}

fun attestSHA512(publicKey: BonehPublicKey, value: ByteArray): BonehAttestation {
    return attest(publicKey, sha512AsBigInt(value), 512)
}

fun binaryRelativitySHA256(value: ByteArray): HashMap<Int, Int> {
    return binaryRelativity(sha256AsBigInt(value), 256)
}

fun binaryRelativitySHA256_4(value: ByteArray): HashMap<Int, Int> {
    return binaryRelativity(sha256_4_AsBigInt(value), 32)
}

fun binaryRelativitySHA512(value: ByteArray): HashMap<Int, Int> {
    return binaryRelativity(sha512AsBigInt(value), 512)
}

fun binaryRelativity(value: BigInteger, bitSpace: Int): HashMap<Int, Int> {
    val out: HashMap<Int, Int> = hashMapOf(0 to 0, 1 to 0, 2 to 0)
    val a = LinkedList((value.toString(2)).map { it.toString().toInt() })
    while (a.size < bitSpace)
        a.push(0)
    for (i in 0 until bitSpace - 1 step 2) {
        val index = a[i] + a[i + 1]
        out[index] = out[index]!! + 1
    }
    out[3] = 0

    return out
}

fun createHonestyCheck(publicKey: BonehPublicKey, value: Int): FP2Value {
    return encode(publicKey, BigInteger(value.toString()))
}

fun attest(publicKey: BonehPublicKey, value: BigInteger, bitSpace: Int): BonehAttestation {
    val a = LinkedList(value.toString(2).toList().map { it.toString().toInt() })
    while (a.size < bitSpace) {
        a.push(0)
    }
    val r = generateModularAdditiveInverse(publicKey.p, bitSpace)
    val tOutPublic = a.zip(r).map { encode(publicKey, BigInteger(it.first.toString()) + it.second) }
    val tOutPrivate = arrayListOf<Pair<Int, FP2Value>>()
    for (i in 0 until a.size - 1 step 2) {
        tOutPrivate.add(Pair(i,
            encode(publicKey, publicKey.p - ((r[i] + r[i + 1]).mod(publicKey.p + BigInteger.ONE)) + BigInteger.ONE)))
    }

    val tOutPublicShuffled = arrayListOf<Triple<Int, FP2Value, FP2Value>>()
    for (i in tOutPublic.indices step 2) {
        tOutPublicShuffled.add(Triple(i, tOutPublic[i], tOutPublic[i + 1]))
    }
    val random = SecureRandom()
    tOutPublicShuffled.shuffle(random)

    val outPublic = arrayListOf<FP2Value>()
    val outPrivate = arrayListOf<Pair<Int, FP2Value>>()
    val shuffleMap = hashMapOf<Int, Int>()

    tOutPublicShuffled.forEach {
        shuffleMap[it.first] = outPublic.size
        outPublic.add(it.second)
        outPublic.add(it.third)
    }

    tOutPrivate.forEach {
        outPrivate.add(Pair(shuffleMap[it.first]!!, it.second))
    }

    outPrivate.shuffle(random)

    val bitPairs = arrayListOf<BitPairAttestation>()
    outPrivate.forEach {
        bitPairs.add(BitPairAttestation(outPublic[it.first], outPublic[it.first + 1], it.second))
    }

    return BonehAttestation(publicKey, bitPairs)
}

fun generateModularAdditiveInverse(p: BigInteger, n: Int): ArrayList<BigInteger> {
    val randoms = arrayListOf<BigInteger>()
    val random = SecureRandom()
    lateinit var randomElement: BigInteger
    for (i in 0 until n - 1) {
        do {
            randomElement = BigInteger(p.bitLength(), random)
        } while (randomElement >= p)
        randoms.add(randomElement)
    }
    var rSum = BigInteger.ZERO
    for (r in randoms) {
        rSum += r
    }

    randoms.add(p - (rSum.mod(p + BigInteger.ONE)) + BigInteger.ONE)
    randoms.shuffle(random)

    return randoms
}

fun createChallenge(publicKey: BonehPublicKey, bitpair: BitPairAttestation): FP2Value {
    return bitpair.compress() * encode(publicKey, BigInteger.ZERO)

}

fun binaryRelativityCertainty(expected: HashMap<Int, Int>, value: HashMap<Int, Int>): Double {
    val exp = value.values.sumOf { it }
    val cert = 1.0 - 0.5.pow(exp)
    return binaryRelativityMatch(expected, value) * cert
}

fun binaryRelativityMatch(expected: HashMap<Int, Int>, value: HashMap<Int, Int>): Double {
    var match = 1.0
    for (k in expected.keys) {
        if (expected[k]!! < value[k]!!) {
            return 0.0
        }
        if (!expected.containsKey(k) || !value.containsKey(k) || expected[k] == 0 || value[k] == 0) {
            continue
        }
        match *= (value[k]!!.toDouble() / expected[k]!!.toDouble())
    }

    return match
}

fun createEmptyRelativityMap(): HashMap<Int, Int> {
    return hashMapOf(0 to 0, 1 to 0, 2 to 0, 3 to 0)
}

fun createChallengeResponseFromPair(privateKey: BonehPrivateKey, pair: Pair<BigInteger, BigInteger>): Int {
    return createChallengeResponse(privateKey, FP2Value(privateKey.p, pair.first, pair.second))
}

fun createChallengeResponse(privateKey: BonehPrivateKey, challenge: FP2Value): Int {
    return decode(privateKey, arrayOf(0, 1, 2), challenge) ?: 3
}

fun internalProcessChallengeResponse(relativityMap: HashMap<Int, Int>, response: Int) {
    synchronized(lock) {
        relativityMap[response] = relativityMap[response]!! + 1
    }
}

