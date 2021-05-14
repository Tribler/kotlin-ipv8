package nl.tudelft.ipv8.attestation.wallet.cryptography.pengbaorange.commitments

import nl.tudelft.ipv8.attestation.wallet.cryptography.bonehexact.BonehPrivateKey
import nl.tudelft.ipv8.attestation.wallet.cryptography.bonehexact.BonehPublicKey
import nl.tudelft.ipv8.attestation.wallet.cryptography.util.PENG_BAO_PRIVATE_COMMITMENT_NUM_PARAMS
import nl.tudelft.ipv8.attestation.wallet.cryptography.util.deserializeFP2Value
import nl.tudelft.ipv8.attestation.wallet.cryptography.util.serializeFP2Value
import nl.tudelft.ipv8.messaging.deserializeAmount
import nl.tudelft.ipv8.messaging.deserializeUChar
import nl.tudelft.ipv8.messaging.serializeUChar
import nl.tudelft.ipv8.messaging.serializeVarLen
import nl.tudelft.ipv8.util.hexToBytes
import nl.tudelft.ipv8.util.toHex
import java.math.BigInteger
import nl.tudelft.ipv8.attestation.wallet.cryptography.bonehexact.decode as bonehExactDecode

class PengBaoPrivateData(
    val m1: BigInteger,
    val m2: BigInteger,
    val m3: BigInteger,
    val r1: BigInteger,
    val r2: BigInteger,
    val r3: BigInteger,
) {

    fun generateResponse(s: BigInteger, t: BigInteger): List<BigInteger> {
        return listOf(
            s * this.m1 + this.m2 + this.m3,
            this.m1 + t * this.m2 + this.m3,
            s * this.r1 + this.r2 + this.r3,
            this.r1 + t * this.r2 + this.r3
        )
    }

    fun serialize(): ByteArray {
        return serializeVarLen(this.m1.toByteArray()) + serializeVarLen(this.m2.toByteArray()) +
            serializeVarLen(this.m3.toByteArray()) + serializeVarLen(this.r1.toByteArray()) +
            serializeVarLen(this.r2.toByteArray()) + serializeVarLen(this.r3.toByteArray())
    }

    fun encode(publicKey: BonehPublicKey): ByteArray {
        val serialized = this.serialize()
        val hexSerialized = serialized.toHex()
        var serializedEncodings = serializeUChar((hexSerialized.length / 2).toUByte())
        for (i in hexSerialized.indices step 2) {
            val intValue = Integer.parseInt(hexSerialized.substring(i, i + 2), 16)
            serializedEncodings += serializeFP2Value(
                nl.tudelft.ipv8.attestation.wallet.cryptography.bonehexact.encode(
                    publicKey,
                    intValue.toBigInteger()
                )
            )
        }
        return serializedEncodings
    }

    companion object {
        val MSG_SPACE = (0 until 256).toList().toTypedArray()

        fun deserialize(serialized: ByteArray): Pair<PengBaoPrivateData, ByteArray> {
            val (values, rem) = deserializeAmount(
                serialized,
                PENG_BAO_PRIVATE_COMMITMENT_NUM_PARAMS
            )
            val m1 = BigInteger(values[0])
            val m2 = BigInteger(values[1])
            val m3 = BigInteger(values[2])
            val r1 = BigInteger(values[3])
            val r2 = BigInteger(values[4])
            val r3 = BigInteger(values[5])

            return Pair(PengBaoPrivateData(m1, m2, m3, r1, r2, r3), rem)
        }

        fun decode(privateKey: BonehPrivateKey, serialized: ByteArray): PengBaoPrivateData {
            var serialization = byteArrayOf()
            val length = deserializeUChar(serialized.copyOfRange(0, 1)).toInt()
            var rem = serialized.copyOfRange(1, serialized.size)
            for (i in 0 until length) {
                val (deserialized, localRem) = deserializeFP2Value(privateKey.g.mod, rem)
                rem = localRem
                val hexedRaw = bonehExactDecode(
                    privateKey,
                    MSG_SPACE,
                    deserialized
                )!!
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
