package nl.tudelft.ipv8.attestation.wallet.cryptography.pengbaorange

import nl.tudelft.ipv8.attestation.wallet.primitives.FP2Value
import nl.tudelft.ipv8.messaging.*
import nl.tudelft.ipv8.util.sha256AsBigInt
import java.math.BigInteger
import java.security.SecureRandom
import kotlin.math.ceil
import kotlin.math.log

fun secureRandomNumber(min: BigInteger, max: BigInteger): BigInteger {
    val normalizedRange = max - min
    // TODO: We lose precision due to double conversion.
    val n = ceil(log(normalizedRange.toDouble(), 2.toDouble()))
    val returnBytes = BigInteger(n.toInt(), SecureRandom())
    val returnValue = min + (returnBytes.mod(normalizedRange))
    return if (returnValue >= min && returnValue < max) returnValue else secureRandomNumber(min, max)
}


private fun pack(vararg numbers: BigInteger): ByteArray {
    var signByte = 0
    var packed = byteArrayOf()

    for (n in numbers) {
        signByte = signByte shl 1
        signByte = signByte or (if (n < BigInteger.ZERO) 1 else 0)
        packed = serializeVarLen(if (n < BigInteger.ZERO) (-n).toByteArray() else n.toByteArray()) + packed
    }

    return serializeUChar(signByte.toUByte()) + packed
}

private fun unpack(serialized: ByteArray, amount: Int): Pair<List<BigInteger>, ByteArray> {
    val buffer = serialized.copyOfRange(1, serialized.size)
    var offset = 0
    val numbers = arrayListOf<BigInteger>()
    var signByte = deserializeUChar(serialized.copyOfRange(0, 1)).toInt()

    while (offset <= buffer.size && numbers.size < amount) {
        val (deserializedValue, localOffset) = deserializeVarLen(buffer, offset)
        offset += localOffset
        val value = BigInteger(deserializedValue)
        val isNegative = ((signByte and 0x01) == 1)
        signByte = signByte shr 1
        numbers.add(if (isNegative) -value else value)
    }
    return Pair(numbers.reversed(), buffer.copyOfRange(offset, buffer.size))
}

const val NUM_PARAMS = 4

class EL(val c: BigInteger, val d: BigInteger, val d1: BigInteger, val d2: BigInteger) {

    fun check(g1: FP2Value, h1: FP2Value, g2: FP2Value, h2: FP2Value, y1: FP2Value, y2: FP2Value): Boolean {
        var cW1 = g1.bigIntPow(this.d) * h1.bigIntPow(this.d1) * y1.bigIntPow(-this.c)
        var cW2 = g2.bigIntPow(this.d) * h2.bigIntPow(this.d2) * y2.bigIntPow(-this.c)
        cW1 = (cW1.wpNominator() * cW1.wpDenomInverse()).normalize()
        cW2 = (cW2.wpNominator() * cW2.wpDenomInverse()).normalize()

        return this.c == (sha256AsBigInt(cW1.a.toString().toByteArray() + cW1.b.toString()
            .toByteArray() + cW2.a.toString().toByteArray() + cW2.b.toString().toByteArray()))
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
                sha256AsBigInt(cW1.a.toString().toByteArray() + cW1.b.toString().toByteArray() + cW2.a.toString()
                    .toByteArray() + cW2.b.toString().toByteArray())

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

class SQR(val f: FP2Value, val el: EL) {

    fun check(g: FP2Value, h: FP2Value, y: FP2Value): Boolean {
        return this.el.check(g, h, this.f, h, this.f, y)
    }

    fun serialize(): ByteArray {
        val minF = this.f.wpCompress()
        return serializeVarLen(minF.mod.toByteArray()) + serializeVarLen(minF.a.toByteArray()) + serializeVarLen(minF.b.toByteArray()) + serializeVarLen(
            this.el.serialize())
    }

    companion object {
        fun create(x: BigInteger, r1: BigInteger, g: FP2Value, h: FP2Value, b: Int, bitSpace: Int): SQR {
            val r2 = secureRandomNumber(-BigInteger("2") xor bitSpace.toBigInteger() * g.mod + BigInteger.ONE,
                BigInteger("2") xor bitSpace.toBigInteger() * g.mod - BigInteger.ONE)
            val f = g.bigIntPow(x) * h.bigIntPow(r2)
            val r3 = r1 - r2 * x
            return SQR(f, EL.create(x, r2, r3, g, h, f, h, b, bitSpace))
        }

        fun deserialize(serialized: ByteArray): Pair<SQR, ByteArray> {
            val (deserialized, rem) = deserializeAmount(serialized, NUM_PARAMS)
            val mod = BigInteger(deserialized[0])
            val a = BigInteger(deserialized[1])
            val b = BigInteger(deserialized[2])
            val (el, _) = EL.deserialize(deserialized[3])
            return Pair(SQR(FP2Value(mod, a, b), el), rem)

        }
    }

    override fun equals(other: Any?): Boolean {
        if (other !is SQR)
            return false
        return (this.f == other.f && this.el == other.el)
    }

    override fun hashCode(): Int {
        return 838182
    }

    override fun toString(): String {
        return "SQR<$f,$el>"
    }
}
