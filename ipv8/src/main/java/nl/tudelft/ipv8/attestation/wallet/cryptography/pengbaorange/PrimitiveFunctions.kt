package nl.tudelft.ipv8.attestation.wallet.cryptography.pengbaorange

import nl.tudelft.ipv8.attestation.wallet.cryptography.bonehexact.BonehPublicKey
import java.math.BigInteger
import java.security.SecureRandom


private fun randomNumber(bitLength: Int): BigInteger {
    return BigInteger(bitLength, SecureRandom())
}

fun createAttestationPair(
    publicKey: BonehPublicKey,
    value: BigInteger,
    a: Int,
    b: Int,
    bitSpace: Int,
): PengBaoAttestation {

    val byteSpace = bitSpace / 8
    val r = randomNumber(bitSpace)
    val ra = randomNumber(bitSpace)
    var raa = randomNumber(bitSpace / 2)
    raa *= raa

    val w = randomNumber(bitSpace)
    val w2 = w * w

    val c = publicKey.g.bigIntPow(value) * publicKey.h.bigIntPow(r)

    val c1 = c / (publicKey.g.bigIntPow((a - 1).toBigInteger()))
    val c2 = publicKey.g.bigIntPow((b + 1).toBigInteger()) / c
    val ca = c1.bigIntPow(b.toBigInteger() - value + BigInteger.ONE) * publicKey.h.bigIntPow(ra)
    val caa = ca.bigIntPow(w2) * publicKey.h.bigIntPow(raa)

    val mst = w2 * (value - a.toBigInteger() + BigInteger.ONE) * (b.toBigInteger() - value + BigInteger.ONE)
    var m4 = BigInteger.ZERO
    while (m4 == BigInteger.ZERO) {
        m4 = randomNumber(bitSpace).mod(sqrt(mst)!! - BigInteger.ONE)
    }
    val m3 = m4 * m4
    var m1 = BigInteger.ZERO
    while (m1 == BigInteger.ZERO) {
        m1 = randomNumber(bitSpace).mod(mst - m4)
    }
    val m2 = mst - m1 - m3

    val rst = w2 * ((b.toBigInteger() - value + BigInteger.ONE) * r + ra) + raa
    var r1 = BigInteger.ZERO
    while (r1 == BigInteger.ZERO) {
        r1 = randomNumber(byteSpace * byteSpace * 8).mod(rst / BigInteger("2") - BigInteger.ONE)
    }
    var r2 = BigInteger.ZERO
    while (r2 == BigInteger.ZERO) {
        r2 = randomNumber(byteSpace * byteSpace * 8).mod(rst / BigInteger("2") - BigInteger.ONE)
    }
    val r3 = rst - r1 - r2

    val ca1 = publicKey.g.bigIntPow(m1) * publicKey.h.bigIntPow(r1)
    val ca2 = publicKey.g.bigIntPow(m2) * publicKey.h.bigIntPow(r2)
    val ca3 = caa / (ca1 * ca2)

    val el = EL.create(b.toBigInteger() - value + BigInteger.ONE,
        -r,
        ra,
        publicKey.g,
        publicKey.h,
        c1,
        publicKey.h,
        b,
        bitSpace)
    val sqr1 = SQR.create(w, raa, ca, publicKey.h, b, bitSpace)
    val sqr2 = SQR.create(m4, r3, publicKey.g, publicKey.h, b, bitSpace)

    val publicData =
        PengBaoPublicData(publicKey, bitSpace, PengBaoCommitment(c, c1, c2, ca, ca1, ca2, ca3, caa), el, sqr1, sqr2)
    val privateData = PengBaoCommitmentPrivate(m1, m2, m3, r1, r2, r3)

    return PengBaoAttestation(publicData, privateData)
}

/*
 * From: https://gist.github.com/JochemKuijpers/cd1ad9ec23d6d90959c549de5892d6cb.
 */
fun sqrt(n: BigInteger): BigInteger? {
    var a = BigInteger.ONE
    var b = n.shiftRight(5).add(BigInteger.valueOf(8))
    while (b.compareTo(a) >= 0) {
        val mid = a.add(b).shiftRight(1)
        if (mid.multiply(mid).compareTo(n) > 0) {
            b = mid.subtract(BigInteger.ONE)
        } else {
            a = mid.add(BigInteger.ONE)
        }
    }
    return a.subtract(BigInteger.ONE)
}
