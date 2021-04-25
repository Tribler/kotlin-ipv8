package nl.tudelft.ipv8.util

import org.json.JSONArray
import org.json.JSONObject

interface EncodingUtils {

    fun encodeBase64(bin: ByteArray): ByteArray

    fun decodeBase64(bin: ByteArray): ByteArray

    fun encodeBase64ToString(bin: ByteArray): String

    fun decodeBase64FromString(encodedString: String): ByteArray
}

var defaultEncodingUtils: EncodingUtils = JavaEncodingUtils

fun JSONObject.asMap(): Map<String, Any?> {
    val results: MutableMap<String, Any?> = HashMap()
    for (key in this.keys()) {
        val value1 = this[key]
        val value: Any? = if (value1 == null || JSONObject.NULL == value1) {
            null
        } else if (value1 is JSONObject) {
            value1.asMap()
        } else if (value1 is JSONArray) {
            value1.toList()
        } else {
            value1
        }
        results[key] = value
    }
    return results
}
