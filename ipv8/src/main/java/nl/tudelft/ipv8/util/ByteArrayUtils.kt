package nl.tudelft.ipv8.util

import java.nio.Buffer
import java.nio.ByteBuffer

class ByteArrayKey(val bytes: ByteArray) {

    override fun equals(other: Any?): Boolean {
        // Note: this is the same as contentEquals.
        return this.contentEquals(other)
    }

    fun contentEquals(other: Any?): Boolean {
        return this === other || (other is ByteArrayKey && this.bytes contentEquals other.bytes)
            || (other is ByteArray && this.bytes contentEquals other)
    }

    override fun hashCode(): Int {
        return bytes.contentHashCode()
    }

    override fun toString(): String {
        return bytes.contentToString()
    }
}

fun ByteArray.toKey(): ByteArrayKey {
    return ByteArrayKey(this)
}

const val BYTES = 8
fun Long.toByteArray(): ByteArray {
    val buffer: ByteBuffer = ByteBuffer.allocate(BYTES)
    buffer.putLong(this)
    return buffer.array()
}

fun ByteArray.toLong(): Long {
    val buffer: ByteBuffer = ByteBuffer.allocate(BYTES)
    buffer.put(this)
    (buffer as Buffer).flip()
    return buffer.getLong()
}
