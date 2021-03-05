package nl.tudelft.ipv8.android.util

import android.util.Base64
import nl.tudelft.ipv8.util.EncodingUtils

object AndroidEncodingUtils : EncodingUtils {
    override fun encodeBase64(bin: ByteArray): ByteArray {
        return Base64.encode(bin, Base64.DEFAULT)
    }

    override fun decodeBase64(bin: ByteArray): ByteArray {
        return Base64.decode(bin, Base64.DEFAULT)
    }

    override fun encodeBase64ToString(bin: ByteArray): String {
        return Base64.encodeToString(bin, Base64.DEFAULT)
    }

    override fun decodeBase64FromString(encodedString: String): ByteArray {
        return Base64.decode(encodedString, Base64.DEFAULT)
    }
}
