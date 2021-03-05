package nl.tudelft.ipv8.util

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
