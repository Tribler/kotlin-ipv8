package nl.tudelft.ipv8.attestation.wallet.cryptography.pengbaorange.boudot.primitives

import nl.tudelft.ipv8.attestation.wallet.cryptography.pengbaorange.boudot.NUM_PARAMS
import nl.tudelft.ipv8.attestation.wallet.cryptography.pengbaorange.boudot.secureRandomNumber
import nl.tudelft.ipv8.attestation.wallet.cryptography.primitives.FP2Value
import nl.tudelft.ipv8.messaging.deserializeAmount
import nl.tudelft.ipv8.messaging.serializeVarLen
import java.math.BigInteger

class SQR(val f: FP2Value, val el: EL) {

    fun check(g: FP2Value, h: FP2Value, y: FP2Value): Boolean {
        return this.el.check(g, h, this.f, h, this.f, y)
    }

    fun serialize(): ByteArray {
        val minF = this.f.wpCompress()
        return serializeVarLen(minF.mod.toByteArray()) + serializeVarLen(minF.a.toByteArray()) + serializeVarLen(minF.b.toByteArray()) + serializeVarLen(
            this.el.serialize()
        )
    }

    companion object {

        fun create(x: BigInteger, r1: BigInteger, g: FP2Value, h: FP2Value, b: Int, bitSpace: Int): SQR {
            val r2 = secureRandomNumber(
                -BigInteger("2") xor bitSpace.toBigInteger() * g.mod + BigInteger.ONE,
                BigInteger("2") xor bitSpace.toBigInteger() * g.mod - BigInteger.ONE
            )
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
