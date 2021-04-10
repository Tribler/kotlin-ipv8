package nl.tudelft.ipv8.attestation.wallet.cryptography.pengbaorange

import nl.tudelft.ipv8.attestation.WalletAttestation
import nl.tudelft.ipv8.attestation.wallet.cryptography.bonehexact.BonehPrivateKey
import nl.tudelft.ipv8.attestation.wallet.cryptography.bonehexact.BonehPublicKey
import nl.tudelft.ipv8.attestation.wallet.cryptography.bonehexact.decode
import nl.tudelft.ipv8.attestation.wallet.cryptography.bonehexact.encode
import nl.tudelft.ipv8.attestation.wallet.primitives.FP2Value
import nl.tudelft.ipv8.messaging.*
import nl.tudelft.ipv8.util.hexToBytes
import nl.tudelft.ipv8.util.toHex
import java.math.BigInteger

private fun serializeFP2Value(value: FP2Value): ByteArray {
    val compressed = value.wpCompress()
    return serializeVarLen(compressed.a.toByteArray()) + serializeVarLen(compressed.b.toByteArray())
}

private fun deserializeFP2Value(mod: BigInteger, serialized: ByteArray): Pair<FP2Value, ByteArray> {
    val (deserialized, rem) = deserializeAmount(serialized, 2)
    val a = BigInteger(deserialized[0])
    val b = BigInteger(deserialized[1])
    return Pair(FP2Value(mod, a, b), rem)
}

const val PENG_BAO_COMMITMENT_NUM_PARAMS = 8
const val PENG_BAO_PRIVATE_COMMITMENT_NUM_PARAMS = 6

class PengBaoCommitment(
    val c: FP2Value,
    val c1: FP2Value,
    val c2: FP2Value,
    val ca: FP2Value,
    val ca1: FP2Value,
    val ca2: FP2Value,
    val ca3: FP2Value,
    val caa: FP2Value,
) {

    fun serialize(): ByteArray {
        return (
            serializeVarLen(this.c.mod.toByteArray()) + serializeFP2Value(this.c) + serializeFP2Value(this.c1)
                + serializeFP2Value(this.c2) + serializeFP2Value(this.ca) + serializeFP2Value(this.ca1)
                + serializeFP2Value(this.ca2) + serializeFP2Value(this.ca3) + serializeFP2Value(this.caa)
            )
    }

    companion object {
        fun deserialize(serialized: ByteArray): Pair<PengBaoCommitment, ByteArray> {
            val (deserializedMod, localOffset) = deserializeVarLen(serialized)
            val mod = BigInteger(deserializedMod)

            var buffer = serialized.copyOfRange(localOffset, serialized.size)
            val params = arrayListOf<FP2Value>()
            for (i in 0 until PENG_BAO_COMMITMENT_NUM_PARAMS) {
                val (param, rem) = deserializeFP2Value(mod, buffer)
                params.add(param)
                buffer = rem
            }

            return Pair(PengBaoCommitment(params[0],
                params[1],
                params[2],
                params[3],
                params[4],
                params[5],
                params[6],
                params[7]), buffer)

        }
    }
}

class PengBaoCommitmentPrivate(
    val m1: BigInteger,
    val m2: BigInteger,
    val m3: BigInteger,
    val r1: BigInteger,
    val r2: BigInteger,
    val r3: BigInteger,
) {


    fun generateResponse(s: BigInteger, t: BigInteger): List<BigInteger> {
        return listOf(s * this.m1 + this.m2 + this.m3,
            this.m1 + t * this.m2 + this.m3,
            s * this.r1 + this.r2 + this.r3,
            this.r1 + t * this.r2 + this.r3)
    }

    fun serialize(): ByteArray {
        return serializeVarLen(this.m1.toByteArray()) + serializeVarLen(this.m2.toByteArray()) + serializeVarLen(this.m3.toByteArray()) + serializeVarLen(
            this.r1.toByteArray()) + serializeVarLen(this.r2.toByteArray()) + serializeVarLen(this.r3.toByteArray())
    }

    fun encode(publicKey: BonehPublicKey): ByteArray {
        val serialized = this.serialize()
        val hexSerialized = serialized.toHex()
        var serializedEncodings = serializeUChar((hexSerialized.length / 2).toUByte())
        for (i in hexSerialized.indices step 2) {
            val intValue = Integer.parseInt(hexSerialized.substring(i, i + 2), 16)
            serializedEncodings += serializeFP2Value(encode(publicKey, intValue.toBigInteger()))
        }
        return serializedEncodings
    }

    companion object {
        val MSG_SPACE = (0 until 256).toList().toTypedArray()

        fun deserialize(serialized: ByteArray): Pair<PengBaoCommitmentPrivate, ByteArray> {
            val (values, rem) = deserializeAmount(serialized, PENG_BAO_PRIVATE_COMMITMENT_NUM_PARAMS)
            val m1 = BigInteger(values[0])
            val m2 = BigInteger(values[1])
            val m3 = BigInteger(values[2])
            val r1 = BigInteger(values[3])
            val r2 = BigInteger(values[4])
            val r3 = BigInteger(values[5])

            return Pair(PengBaoCommitmentPrivate(m1, m2, m3, r1, r2, r3), rem)
        }

        fun decode(privateKey: BonehPrivateKey, serialized: ByteArray): PengBaoCommitmentPrivate {
            var serialization = byteArrayOf()
            val length = deserializeUChar(serialized.copyOfRange(0, 1)).toInt()
            var rem = serialized.copyOfRange(1, serialized.size)
            for (i in 0 until length) {
                val (deserialized, localRem) = deserializeFP2Value(privateKey.g.mod, rem)
                rem = localRem
                var hexedRaw = decode(privateKey, MSG_SPACE, deserialized)!!
                var hexed = hexedRaw.toString(16)
                if (hexed.endsWith("L", true)) {
                    hexed = hexed.substring(0, hexed.lastIndex)
                }
                if (hexed.length % 2 == 1) {
                    hexed = "0$hexed"
                }
                serialization += hexed.hexToBytes()
            }
            return deserialize(serialization).first
        }
    }

}

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
        var out = this.el.check(this.publicKey.g,
            this.publicKey.h,
            this.commitment.c1,
            this.publicKey.h,
            this.commitment.c2,
            this.commitment.ca)
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

class PengBaoAttestation(
    val publicData: PengBaoPublicData,
    val privateData: PengBaoCommitmentPrivate?,
    override val idFormat: String? = null,
) : WalletAttestation() {

    override val publicKey = publicData.publicKey

    override fun serialize(): ByteArray {
        return this.publicData.serialize()
    }

    override fun deserialize(serialized: ByteArray, idFormat: String): WalletAttestation {
        return PengBaoAttestation.deserialize(serialized, idFormat)
    }

    override fun serializePrivate(publicKey: BonehPublicKey): ByteArray {
        return if (this.privateData != null) {
            val publicData = this.publicData.serialize()
            val privateData = this.privateData.encode(publicKey)
            publicData + privateData
        } else {
            throw RuntimeException("Private data was null.")
        }
    }

    override fun deserializePrivate(
        privateKey: BonehPrivateKey,
        serialized: ByteArray,
        idFormat: String?,
    ): WalletAttestation {
        return PengBaoAttestation.deserializePrivate(privateKey, serialized, idFormat)
    }

    companion object {
        fun deserialize(serialized: ByteArray, idFormat: String? = null): PengBaoAttestation {
            val (pubicData, _) = PengBaoPublicData.deserialize(serialized)
            return PengBaoAttestation(pubicData, null, idFormat)
        }

        fun deserializePrivate(
            privateKey: BonehPrivateKey,
            serialized: ByteArray,
            idFormat: String? = null,
        ): PengBaoAttestation {
            val (publicData, rem) = PengBaoPublicData.deserialize(serialized)
            val privateData = PengBaoCommitmentPrivate.decode(privateKey, rem)

            return PengBaoAttestation(publicData, privateData, idFormat)
        }
    }
}
