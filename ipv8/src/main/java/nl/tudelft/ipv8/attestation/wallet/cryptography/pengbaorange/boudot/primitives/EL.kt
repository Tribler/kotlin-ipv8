package nl.tudelft.ipv8.attestation.wallet.cryptography.pengbaorange.boudot.primitives

import nl.tudelft.ipv8.attestation.wallet.cryptography.pengbaorange.boudot.NUM_PARAMS
import nl.tudelft.ipv8.attestation.wallet.cryptography.pengbaorange.boudot.pack
import nl.tudelft.ipv8.attestation.wallet.cryptography.pengbaorange.boudot.secureRandomNumber
import nl.tudelft.ipv8.attestation.wallet.cryptography.pengbaorange.boudot.unpack
import nl.tudelft.ipv8.attestation.wallet.cryptography.primitives.FP2Value
import nl.tudelft.ipv8.util.sha256AsBigInt
import java.math.BigInteger

class EL(val c: BigInteger, val d: BigInteger, val d1: BigInteger, val d2: BigInteger) {

    fun check(g1: FP2Value, h1: FP2Value, g2: FP2Value, h2: FP2Value, y1: FP2Value, y2: FP2Value): Boolean {
        var cW1 = g1.bigIntPow(this.d) * h1.bigIntPow(this.d1) * y1.bigIntPow(-this.c)
        var cW2 = g2.bigIntPow(this.d) * h2.bigIntPow(this.d2) * y2.bigIntPow(-this.c)
        cW1 = (cW1.wpNominator() * cW1.wpDenomInverse()).normalize()
        cW2 = (cW2.wpNominator() * cW2.wpDenomInverse()).normalize()

        return this.c == (sha256AsBigInt(
            cW1.a.toString().toByteArray() + cW1.b.toString()
                .toByteArray() + cW2.a.toString().toByteArray() + cW2.b.toString().toByteArray()
        ))
    }

    fun serialize(): ByteArray {
        return pack(this.c, this.d, this.d1, this.d2)
    }

    companion object {
        fun create(
            x: BigInteger,
            r1: BigInteger,
            r2: BigInteger,
            g1: FP2Value,
            h1: FP2Value,
            g2: FP2Value,
            h2: FP2Value,
            b: Int,
            bitSpace: Int,
            t: Int = 80,
            l: Int = 40,
        ): EL {
            val maxRangeW = 2 xor (l + t) * b - 1
            val maxRangeN = BigInteger("2") xor (l + t + bitSpace).toBigInteger() * g1.mod - BigInteger.ONE
            val w = secureRandomNumber(BigInteger.ONE, maxRangeW.toBigInteger())
            val n1 = secureRandomNumber(BigInteger.ONE, maxRangeN)
            val n2 = secureRandomNumber(BigInteger.ONE, maxRangeN)
            val w1 = g1.bigIntPow(w) * h1.bigIntPow(n1)
            val w2 = g2.bigIntPow(w) * h2.bigIntPow(n2)
            val cW1 = (w1.wpNominator() * w1.wpDenomInverse()).normalize()
            val cW2 = (w2.wpNominator() * w2.wpDenomInverse()).normalize()

            val c =
                sha256AsBigInt(
                    cW1.a.toString().toByteArray() + cW1.b.toString().toByteArray() + cW2.a.toString()
                        .toByteArray() + cW2.b.toString().toByteArray()
                )

            val d = w + c * x
            val d1 = n1 + c * r1
            val d2 = n2 + c * r2

            return EL(c, d, d1, d2)
        }

        fun deserialize(serialized: ByteArray): Pair<EL, ByteArray> {
            val (values, remainder) = unpack(serialized, NUM_PARAMS)
            return Pair(EL(values[0], values[1], values[2], values[3]), remainder)
        }
    }

    override fun equals(other: Any?): Boolean {
        if (other !is EL)
            return false
        return (this.c == other.c && this.d == other.d && this.d1 == other.d1 && this.d2 == other.d2)
    }

    override fun hashCode(): Int {
        return 6976
    }

    override fun toString(): String {
        return "EL<$c,$d,$d1,$d2>"
    }
}
