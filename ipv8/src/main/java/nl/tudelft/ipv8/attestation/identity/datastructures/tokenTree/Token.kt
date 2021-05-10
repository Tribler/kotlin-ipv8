package nl.tudelft.ipv8.attestation.identity.datastructures.tokenTree

import nl.tudelft.ipv8.attestation.identity.datastructures.SignedObject
import nl.tudelft.ipv8.keyvault.PrivateKey
import nl.tudelft.ipv8.keyvault.PublicKey
import nl.tudelft.ipv8.util.sha3_256
import nl.tudelft.ipv8.util.toHex

class Token(
    val previousTokenHash: ByteArray,
    content: ByteArray? = null,
    contentHash: ByteArray? = null,
    privateKey: PrivateKey? = null,
    signature: ByteArray? = null,
) : SignedObject(privateKey, signature) {

    var content: ByteArray? = null
    var contentHash: ByteArray

    init {
        if (content != null && contentHash == null) {
            this.content = content
            this.contentHash = sha3_256(content)
        } else if (content == null && contentHash != null) {
            this.contentHash = contentHash
        } else throw RuntimeException("Specify either `content` or `content_hash`.")
        super.init()
    }

    override fun getPlaintext(): ByteArray {
        return this.previousTokenHash + this.contentHash
    }

    override fun deserialize(data: ByteArray, publicKey: PublicKey, offset: Int): Token {
        return Companion.deserialize(data, publicKey, offset)
    }

    fun receiveContent(content: ByteArray): Boolean {
        val contentHash = sha3_256(content)
        if (contentHash.contentEquals(this.contentHash)) {
            this.content = content
            return true
        }
        return false
    }

    fun toDatabaseTuple(): Array<ByteArray?> {
        return arrayOf(this.previousTokenHash, this.signature, this.contentHash, this.content)
    }

    override fun toString(): String {
        return "Token[${this.hash.toHex()}](${this.previousTokenHash.toHex()}, ${contentHash.toHex()})"
    }

    operator fun component1(): ByteArray {
        return this.previousTokenHash
    }

    operator fun component2(): ByteArray {
        return this.signature
    }

    operator fun component3(): ByteArray {
        return this.contentHash
    }

    operator fun component4(): ByteArray? {
        return this.content
    }

    companion object {
        fun deserialize(data: ByteArray, publicKey: PublicKey, offset: Int = 0): Token {
            val sigLength = publicKey.getSignatureLength()
            return Token(data.copyOfRange(offset, offset + 32),
                contentHash = data.copyOfRange(offset + 32, offset + 64),
                signature = data.copyOfRange(offset + 64, offset + 64 + sigLength))
        }

        fun create(previousToken: Token, content: ByteArray, privateKey: PrivateKey): Token {
            return Token(previousToken.hash, content, privateKey = privateKey)
        }

        fun fromDatabaseTuple(
            previousTokenHash: ByteArray,
            signature: ByteArray?,
            contentHash: ByteArray?,
            content: ByteArray?,
        ): Token {
            val token = Token(previousTokenHash, contentHash = contentHash, signature = signature)
            if (content != null) {
                token.receiveContent(content)
            }
            return token
        }
    }


}
