package nl.tudelft.ipv8.util

import org.bouncycastle.jcajce.provider.digest.SHA3
import org.bouncycastle.jcajce.provider.digest.SHA3.DigestSHA3
import java.math.BigInteger
import java.security.MessageDigest

private const val SHA1 = "SHA-1"
private const val SHA256 = "SHA-256"
private const val SHA512 = "SHA-512"
private const val SHA3_256 = "SHA3-256"
private val SHA1_PADDING = "SHA-1".toByteArray() + byteArrayOf(0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00)

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
    val digestSHA3: DigestSHA3 = SHA3.Digest256()
    return digestSHA3.digest(input)
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

fun stripSHA1Padding(input: ByteArray): ByteArray {
    val prefix = input.copyOfRange(0, 12)
    return if (prefix.contentEquals(SHA1_PADDING)) input.copyOfRange(12, input.size) else input
}

fun padSHA1Hash(attributeHash: ByteArray): ByteArray {
    return SHA1_PADDING + attributeHash
}



