package nl.tudelft.ipv8.attestation.wallet.cryptography.bonehexact

import nl.tudelft.ipv8.attestation.wallet.primitives.FP2Value
import nl.tudelft.ipv8.attestation.wallet.primitives.weilParing
import java.math.BigInteger
import java.security.KeyPairGenerator
import java.security.SecureRandom
import java.security.interfaces.RSAPrivateCrtKey
import java.security.spec.RSAKeyGenParameterSpec
import org.bouncycastle.math.ec.WNafUtil
import org.bouncycastle.util.BigIntegers

fun encode(publicKey: BonehPublicKey, m: BigInteger): FP2Value {
    return publicKey.g.bigIntPow(m) * getRandomExponentiation(publicKey.h, publicKey.p)
}

fun decode(privateKey: BonehPrivateKey, messageSpace: Array<Int>, c: FP2Value): Int? {
    val d = c.bigIntPow(privateKey.t1)
    val t = privateKey.g.bigIntPow(privateKey.t1)
    for (m in messageSpace) {
        if (d == t.bigIntPow(m.toBigInteger()))
            return m
    }
    return null
}

fun getRandomExponentiation(p: FP2Value, n: BigInteger): FP2Value {
    val random = SecureRandom()
    val r = generateRandomBigInteger(n - BigInteger.ONE, BigInteger("4"), random)
    var test = p.bigIntPow(r)
    while (test == FP2Value(p.mod, BigInteger.ONE))
        test = p.bigIntPow(generateRandomBigInteger(n - BigInteger.ONE, BigInteger("4"), random))
    return test
}

fun generateKeypair(keySize: Int = 32): Pair<BonehPublicKey, BonehPrivateKey> {
    val (t1, t2) = generatePrimes(keySize)
    val n = t1 * t2
    val (p, g) = getGoodWp(n)
    var u: FP2Value? = null
    while (u == null || (u.bigIntPow(t2) == FP2Value(p, BigInteger.ONE)))
        u = getGoodWp(n, p).second

    val h = u.bigIntPow(t2)
    return Pair(BonehPublicKey(p, g, h), BonehPrivateKey(p, g, h, t1 * t2, t1))
}

fun getGoodWp(n: BigInteger, p: BigInteger = generatePrime(n)): Pair<BigInteger, FP2Value> {
    var wp: FP2Value? = null

    while (wp == null || !isGoodWp(n, wp)) {
        val (g1x, g1y) = getRandomBase(n)
        wp = bilinearGroup(n, p, g1x, g1y, g1x, g1y)
        if (!isGoodWp(n, wp))
            wp = wp.bigIntPow((p + BigInteger.ONE) / n)
    }
    return Pair(p, wp)

}

fun bilinearGroup(
    n: BigInteger,
    p: BigInteger,
    g1x: BigInteger,
    g1y: BigInteger,
    g2x: BigInteger,
    g2y: BigInteger,
): FP2Value {
    return try {
        weilParing(p,
            n,
            Pair(FP2Value(p, g1x), FP2Value(p, g1y)),
            Pair(FP2Value(p, b = g2x), FP2Value(p, g2y)),
            Pair(FP2Value(p), FP2Value(p)))
    } catch (e: Exception) {
        FP2Value(p)
    }
}

fun getRandomBase(n: BigInteger): Pair<BigInteger, BigInteger> {
    val random = SecureRandom()
    val x = generateRandomBigInteger(n, random = random)
    val y = generateRandomBigInteger(n, random = random)

    return Pair(x, y)
}

fun isGoodWp(n: BigInteger, wp: FP2Value): Boolean {
    val isOne = wp == FP2Value(wp.mod, BigInteger.ONE)
    val isZero = wp == FP2Value(wp.mod)
    val goodOrder = wp.bigIntPow(n + BigInteger.ONE) == wp
    return goodOrder && !isZero && !isOne
}

fun generatePrime(n: BigInteger): BigInteger {
    var p = BigInteger.ONE
    var l = BigInteger.ZERO
    while ((p.mod(BigInteger("3")) != BigInteger("2")) || !p.isProbablePrime(100)) {
        l += BigInteger.ONE
        p = l * n - BigInteger.ONE
    }
    return p
}

fun generatePrimes(keySize: Int = 128): Pair<BigInteger, BigInteger> {
    val p: BigInteger
    val q: BigInteger
    if (keySize >= 512) {
        val rsa = KeyPairGenerator.getInstance("RSA")
        rsa.initialize(RSAKeyGenParameterSpec(keySize, BigInteger("65537")))
        val privateKey = rsa.genKeyPair().private as RSAPrivateCrtKey
        p = privateKey.primeP
        q = privateKey.primeQ
    } else {
        val primes = generateSafePrimes(keySize, 2, SecureRandom())!!
        p = primes[0]
        q = primes[1]
    }

    return Pair(p.min(q), p.max(q))
}

// Src: https://github.com/bcgit/bc-java/blob/bc3b92f1f0e78b82e2584c5fb4b226a13e7f8b3b/core/src/main/java/org/bouncycastle/crypto/generators/DHParametersHelper.java
fun generateSafePrimes(size: Int, certainty: Int, random: SecureRandom?): Array<BigInteger>? {
    var p: BigInteger
    var q: BigInteger
    val qLength = size - 1
    val minWeight = size ushr 2
    while (true) {
        q = BigIntegers.createRandomPrime(qLength, 2, random)

        // p <- 2q + 1
        p = q.shiftLeft(1).add(BigInteger.valueOf(1))
        if (!p.isProbablePrime(certainty)) {
            continue
        }
        if (certainty > 2 && !q.isProbablePrime(certainty - 2)) {
            continue
        }

        /*
         * Require a minimum weight of the NAF representation, since low-weight primes may be
         * weak against a version of the number-field-sieve for the discrete-logarithm-problem.
         *
         * See "The number field sieve for integers of low weight", Oliver Schirokauer.
         */
        if (WNafUtil.getNafWeight(p) < minWeight) {
            continue
        }
        break
    }
    return arrayOf(p, q)
}

fun generateRandomBigInteger(
    upperBound: BigInteger,
    lowerBound: BigInteger = -BigInteger.ONE,
    random: SecureRandom = SecureRandom(),
): BigInteger {
    var randomElement: BigInteger
    do {
        randomElement = BigInteger(upperBound.bitLength(), random)
    } while (randomElement >= upperBound || randomElement <= lowerBound)
    return randomElement
}
