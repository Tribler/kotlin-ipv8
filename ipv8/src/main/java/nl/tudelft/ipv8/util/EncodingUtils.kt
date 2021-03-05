package nl.tudelft.ipv8.util

interface EncodingUtils {

    fun encodeBase64(bin: ByteArray): ByteArray

    fun decodeBase64(bin: ByteArray): ByteArray

    fun encodeBase64ToString(bin: ByteArray): String

    fun decodeBase64FromString(encodedString: String): ByteArray
}

var defaultEncodingUtils: EncodingUtils = JavaEncodingUtils
