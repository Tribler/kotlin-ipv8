package nl.tudelft.ipv8.attestation.wallet.cryptography.pengbaorange.commitments

import nl.tudelft.ipv8.attestation.wallet.cryptography.primitives.FP2Value
import nl.tudelft.ipv8.attestation.wallet.cryptography.util.PENG_BAO_COMMITMENT_NUM_PARAMS
import nl.tudelft.ipv8.attestation.wallet.cryptography.util.deserializeFP2Value
import nl.tudelft.ipv8.attestation.wallet.cryptography.util.serializeFP2Value
import nl.tudelft.ipv8.messaging.deserializeVarLen
import nl.tudelft.ipv8.messaging.serializeVarLen
import java.math.BigInteger

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

            return Pair(
                PengBaoCommitment(
                    params[0],
                    params[1],
                    params[2],
                    params[3],
                    params[4],
                    params[5],
                    params[6],
                    params[7]
                ), buffer
            )
        }
    }
}
