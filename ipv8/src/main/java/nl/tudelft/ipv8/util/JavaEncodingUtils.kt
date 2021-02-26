package nl.tudelft.ipv8.util

import java.util.*

@Suppress("NewApi")
// This object should not be used in Android.
object JavaEncodingUtils : EncodingUtils {

    override fun encodeBase64(bin: ByteArray): ByteArray {
        return Base64.getEncoder().encode(bin)
    }

    override fun decodeBase64(bin: ByteArray): ByteArray {
        return Base64.getDecoder().decode(bin)
    }

    override fun encodeBase64ToString(bin: ByteArray): String {
        return Base64.getEncoder().encodeToString(bin)
    }

    override fun decodeBase64FromString(encodedString: String): ByteArray {
        return Base64.getDecoder().decode(encodedString)
    }
}
