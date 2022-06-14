package nl.tudelft.ipv8.attestation.wallet.cryptography.pengbaorange.boudot

import nl.tudelft.ipv8.messaging.*
import java.math.BigInteger
import java.security.SecureRandom
import kotlin.math.ceil
import kotlin.math.log

const val NUM_PARAMS = 4

fun secureRandomNumber(min: BigInteger, max: BigInteger): BigInteger {
    val normalizedRange = max - min
    // TODO: We lose precision due to double conversion.
    val n = ceil(log(normalizedRange.toDouble(), 2.toDouble()))
    val returnBytes = BigInteger(n.toInt(), SecureRandom())
    val returnValue = min + (returnBytes.mod(normalizedRange))
    return if (returnValue >= min && returnValue < max) returnValue else secureRandomNumber(min, max)
}

fun pack(vararg numbers: BigInteger): ByteArray {
    var signByte = 0
    var packed = byteArrayOf()

    for (n in numbers) {
        signByte = signByte shl 1
        signByte = signByte or (if (n < BigInteger.ZERO) 1 else 0)
        packed = serializeVarLen(if (n < BigInteger.ZERO) (-n).toByteArray() else n.toByteArray()) + packed
    }

    return serializeUChar(signByte.toUByte()) + packed
}

fun unpack(serialized: ByteArray, amount: Int): Pair<List<BigInteger>, ByteArray> {
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
