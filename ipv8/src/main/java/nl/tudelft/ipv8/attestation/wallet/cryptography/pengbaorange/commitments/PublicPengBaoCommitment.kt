package nl.tudelft.ipv8.attestation.wallet.cryptography.pengbaorange.commitments

import nl.tudelft.ipv8.attestation.wallet.cryptography.bonehexact.BonehPublicKey
import nl.tudelft.ipv8.attestation.wallet.cryptography.pengbaorange.boudot.primitives.EL
import nl.tudelft.ipv8.attestation.wallet.cryptography.pengbaorange.boudot.primitives.SQR
import nl.tudelft.ipv8.messaging.deserializeUChar
import nl.tudelft.ipv8.messaging.serializeUChar
import java.math.BigInteger

class PengBaoPublicData(
    val publicKey: BonehPublicKey,
    val bitSpace: Int,
    val commitment: PengBaoCommitment,
    val el: EL,
    val sqr1: SQR,
    val sqr2: SQR,
) {

    fun check(
        a: Int,
        b: Int,
        s: BigInteger,
        t: BigInteger,
        x: BigInteger,
        y: BigInteger,
        u: BigInteger,
        v: BigInteger,
    ): Boolean {
        var out = this.el.check(
            this.publicKey.g,
            this.publicKey.h,
            this.commitment.c1,
            this.publicKey.h,
            this.commitment.c2,
            this.commitment.ca
        )
        out = out && this.sqr1.check(this.commitment.ca, this.publicKey.h, this.commitment.caa)
        out = out && this.sqr2.check(this.publicKey.g, this.publicKey.h, this.commitment.ca3)
        out =
            out && this.commitment.c1 == this.commitment.c / this.publicKey.g.bigIntPow(a.toBigInteger() - BigInteger.ONE)
        out =
            out && this.commitment.c2 == this.publicKey.g.bigIntPow(b.toBigInteger() + BigInteger.ONE) / this.commitment.c
        out = out && this.commitment.caa == this.commitment.ca1 * this.commitment.ca2 * this.commitment.ca3
        out = out && (this.publicKey.g.bigIntPow(x) * this.publicKey.h.bigIntPow(u)
            == (this.commitment.ca1.bigIntPow(s) * this.commitment.ca2 * this.commitment.ca3))
        out = out && ((this.publicKey.g.bigIntPow(y) * this.publicKey.h.bigIntPow(v))
            == (this.commitment.ca1 * this.commitment.ca2.bigIntPow(t) * this.commitment.ca3))

        return out && x > BigInteger.ZERO && y > BigInteger.ZERO
    }

    fun serialize(): ByteArray {
        return (this.publicKey.serialize() + serializeUChar(this.bitSpace.toUByte()) + this.commitment.serialize()
            + this.el.serialize() + this.sqr1.serialize() + this.sqr2.serialize())
    }

    companion object {
        fun deserialize(serialized: ByteArray): Pair<PengBaoPublicData, ByteArray> {
            val publicKey = BonehPublicKey.deserialize(serialized)!!
            var rem = serialized.copyOfRange(publicKey.serialize().size, serialized.size)
            val bitSpace = deserializeUChar(rem.copyOfRange(0, 1)).toInt()
            rem = rem.copyOfRange(1, rem.size)
            val (commitment, localRem) = PengBaoCommitment.deserialize(rem)
            rem = localRem
            val (el, localRem2) = EL.deserialize(rem)
            rem = localRem2
            val (sqr1, localRem3) = SQR.deserialize(rem)
            rem = localRem3
            val (sqr2, localRem4) = SQR.deserialize(rem)
            rem = localRem4

            return Pair(PengBaoPublicData(publicKey, bitSpace, commitment, el, sqr1, sqr2), rem)
        }
    }
}
