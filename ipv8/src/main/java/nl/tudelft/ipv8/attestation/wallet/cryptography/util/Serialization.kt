package nl.tudelft.ipv8.attestation.wallet.cryptography.util

import nl.tudelft.ipv8.attestation.wallet.cryptography.primitives.FP2Value
import nl.tudelft.ipv8.messaging.deserializeAmount
import nl.tudelft.ipv8.messaging.serializeVarLen
import java.math.BigInteger

const val PENG_BAO_COMMITMENT_NUM_PARAMS = 8
const val PENG_BAO_PRIVATE_COMMITMENT_NUM_PARAMS = 6

fun serializeFP2Value(value: FP2Value): ByteArray {
    val compressed = value.wpCompress()
    return serializeVarLen(compressed.a.toByteArray()) + serializeVarLen(compressed.b.toByteArray())
}

fun deserializeFP2Value(mod: BigInteger, serialized: ByteArray): Pair<FP2Value, ByteArray> {
    val (deserialized, rem) = deserializeAmount(serialized, 2)
    val a = BigInteger(deserialized[0])
    val b = BigInteger(deserialized[1])
    return Pair(FP2Value(mod, a, b), rem)
}
