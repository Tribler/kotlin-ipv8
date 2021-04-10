package nl.tudelft.ipv8.messaging

import java.nio.Buffer
import java.nio.ByteBuffer

const val SERIALIZED_USHORT_SIZE = 2
const val SERIALIZED_UINT_SIZE = 4
const val SERIALIZED_ULONG_SIZE = 8
const val SERIALIZED_LONG_SIZE = 4
const val SERIALIZED_UBYTE_SIZE = 1

const val SERIALIZED_PUBLIC_KEY_SIZE = 74
const val HASH_SIZE = 32
const val SIGNATURE_SIZE = 64
const val SERIALIZED_SHA1_HASH_SIZE = 20

interface Serializable {
    fun serialize(): ByteArray
}

interface Deserializable<T> {
    fun deserialize(buffer: ByteArray, offset: Int = 0): Pair<T, Int>
}

fun serializeBool(data: Boolean): ByteArray {
    val value = if (data) 1 else 0
    val array = ByteArray(1)
    array[0] = value.toByte()
    return array
}

fun deserializeBool(buffer: ByteArray, offset: Int = 0): Boolean {
    return buffer[offset].toInt() > 0
}

fun serializeUShort(value: Int): ByteArray {
    val bytes = ByteArray(SERIALIZED_USHORT_SIZE)
    bytes[1] = (value and 0xFF).toByte()
    bytes[0] = ((value shr 8) and 0xFF).toByte()
    return bytes
}

fun deserializeUShort(buffer: ByteArray, offset: Int = 0): Int {
    return (((buffer[offset].toInt() and 0xFF) shl 8) or (buffer[offset + 1].toInt() and 0xFF))
}

fun serializeUInt(value: UInt): ByteArray {
    val bytes = UByteArray(SERIALIZED_UINT_SIZE)
    for (i in 0 until SERIALIZED_UINT_SIZE) {
        bytes[i] = ((value shr ((7 - i) * 8)) and 0xFFu).toUByte()
    }
    return bytes.toByteArray()
}

fun deserializeUInt(buffer: ByteArray, offset: Int = 0): UInt {
    val ubuffer = buffer.toUByteArray()
    var result = 0u
    for (i in 0 until SERIALIZED_UINT_SIZE) {
        result = (result shl 8) or ubuffer[offset + i].toUInt()
    }
    return result
}

fun serializeULong(value: ULong): ByteArray {
    val bytes = UByteArray(SERIALIZED_ULONG_SIZE)
    for (i in 0 until SERIALIZED_ULONG_SIZE) {
        bytes[i] = ((value shr ((7 - i) * 8)) and 0xFFu).toUByte()
    }
    return bytes.toByteArray()
}

fun deserializeULong(buffer: ByteArray, offset: Int = 0): ULong {
    val ubuffer = buffer.toUByteArray()
    var result = 0uL
    for (i in 0 until SERIALIZED_ULONG_SIZE) {
        result = (result shl 8) or ubuffer[offset + i].toULong()
    }
    return result
}

fun serializeLong(value: Long): ByteArray {
    val buffer = ByteBuffer.allocate(SERIALIZED_LONG_SIZE)
    buffer.putInt(value.toInt())
    return buffer.array()
}

fun deserializeLong(bytes: ByteArray, offset: Int = 0): Long {
    val buffer = ByteBuffer.allocate(SERIALIZED_LONG_SIZE)
    buffer.put(bytes.copyOfRange(offset, offset + SERIALIZED_LONG_SIZE))
    // In JDK 8 this returns a Buffer.
    (buffer as Buffer).flip()
    return buffer.int.toLong()
}

fun serializeUChar(char: UByte): ByteArray {
    return byteArrayOf(char.toByte())
}

fun deserializeUChar(buffer: ByteArray, offset: Int = 0): UByte {
    val ubuffer = buffer.toUByteArray()
    return ubuffer[offset]
}

fun serializeVarLen(bytes: ByteArray): ByteArray {
    return serializeUInt(bytes.size.toUInt()) + bytes
}

fun deserializeVarLen(buffer: ByteArray, offset: Int = 0): Pair<ByteArray, Int> {
    val len = deserializeUInt(buffer, offset).toInt()
    val payload = buffer.copyOfRange(offset + SERIALIZED_UINT_SIZE,
        offset + SERIALIZED_UINT_SIZE + len)
    return Pair(payload, SERIALIZED_UINT_SIZE + len)
}

fun deserializeRecursively(buffer: ByteArray, offset: Int = 0): Array<ByteArray> {
    if (buffer.isEmpty()) {
        return arrayOf()
    }
    val len = deserializeUInt(buffer, offset).toInt()
    val payload = buffer.copyOfRange(offset + SERIALIZED_UINT_SIZE,
        offset + SERIALIZED_UINT_SIZE + len)
    return arrayOf(payload) + deserializeRecursively(buffer.copyOfRange(offset + SERIALIZED_UINT_SIZE + len,
        buffer.size), offset)
}

fun deserializeAmount(buffer: ByteArray, amount: Int, offset: Int = 0): Pair<Array<ByteArray>, ByteArray> {
    val returnValues = arrayListOf<ByteArray>()
    var localOffset = offset
    for (i in 0 until amount) {
        val len = deserializeUInt(buffer, localOffset).toInt()
        val payload = buffer.copyOfRange(localOffset + SERIALIZED_UINT_SIZE,
            localOffset + SERIALIZED_UINT_SIZE + len)
        localOffset += SERIALIZED_UINT_SIZE + len
        returnValues.add(payload)
    }
    return Pair(returnValues.toTypedArray(), buffer.copyOfRange(localOffset, buffer.size))
}

/**
 * Can only be used as the last element in a payload as it will consume the remainder of the
 * input (avoid if possible).
 */
fun deserializeRaw(buffer: ByteArray, offset: Int = 0): Pair<ByteArray, Int> {
    val len = buffer.size - offset
    return Pair(buffer.copyOfRange(offset, buffer.size), len)
}
