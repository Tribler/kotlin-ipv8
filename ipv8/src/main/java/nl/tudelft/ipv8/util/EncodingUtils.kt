package nl.tudelft.ipv8.util

import org.json.JSONArray
import org.json.JSONObject
import java.util.ArrayList

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
            value1.asList()
        } else {
            value1
        }
        results[key] = value
    }
    return results
}

fun JSONArray.asList(): List<Any?> {
    val results: MutableList<Any?> = ArrayList(this.length())
    for (i in 0 until this.length()) {
        val element = this.get(i)
        if (element == null || JSONObject.NULL == element) {
            results.add(null)
        } else if (element is JSONArray) {
            results.add(element.asList())
        } else if (element is JSONObject) {
            results.add(element.asMap())
        } else {
            results.add(element)
        }
    }
    return results
}
