package nl.tudelft.ipv8.util

import java.math.BigInteger
import java.security.MessageDigest

private const val SHA1 = "SHA-1"
private const val SHA256 = "SHA-256"
private const val SHA512 = "SHA-512"
private const val SHA3_256 = "SHA3-256"

fun sha1(input: ByteArray): ByteArray {
    return MessageDigest
        .getInstance(SHA1)
        .digest(input)
}

fun sha256(input: ByteArray): ByteArray {
    return MessageDigest
        .getInstance(SHA256)
        .digest(input)
}

fun sha512(input: ByteArray): ByteArray {
    return MessageDigest
        .getInstance(SHA512)
        .digest(input)
}

fun sha3_256(input: ByteArray): ByteArray {
    return MessageDigest.getInstance(SHA3_256).digest(input)
}


fun toASCII(value: String): ByteArray {
    return value.toByteArray(Charsets.US_ASCII)
}

fun sha256AsBigInt(input: ByteArray): BigInteger {
    var out = BigInteger.ZERO
    val hash = sha256(input)
    for (i in 0..hash.lastIndex) {
        out = out shl 8
        out = out or BigInteger(hash[i].toUByte().toString())
    }
    return out
}

fun sha256_4_AsBigInt(input: ByteArray): BigInteger {
    var out = BigInteger.ZERO
    val hash = sha256(input).copyOfRange(0, 4)
    for (i in 0..hash.lastIndex) {
        out = out shl 8
        out = out or BigInteger(hash[i].toUByte().toString())
    }
    return out
}

fun sha512AsBigInt(input: ByteArray): BigInteger {
    var out = BigInteger.ZERO
    val hash = sha512(input).copyOfRange(0, 4)
    for (i in 0..hash.lastIndex) {
        out = out shl 8
        out = out or BigInteger(hash[i].toUByte().toString())
    }
    return out
}
