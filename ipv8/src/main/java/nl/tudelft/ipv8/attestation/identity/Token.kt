package nl.tudelft.ipv8.attestation.identity

import nl.tudelft.ipv8.attestation.SignedObject
import nl.tudelft.ipv8.keyvault.PrivateKey
import nl.tudelft.ipv8.keyvault.PublicKey
import nl.tudelft.ipv8.util.sha3_256
import nl.tudelft.ipv8.util.toHex

class Token(
    private val previousTokenHash: ByteArray,
    content: ByteArray? = null,
    contentHash: ByteArray? = null,
    privateKey: PrivateKey? = null,
    signature: ByteArray? = null
) : SignedObject(privateKey, signature) {

    private var content: ByteArray? = null
    var contentHash: ByteArray

    init {
        if (content != null && contentHash == null) {
            this.content = content
            this.contentHash = sha3_256(content)
        } else if (content == null && contentHash != null) {
            this.contentHash = contentHash
        } else throw RuntimeException("Specify either `content` or `content_hash`.")
    }

    override fun getPlaintext(): ByteArray {
        return this.previousTokenHash + this.contentHash
    }

    override fun deserialize(data: ByteArray, publicKey: PublicKey, offset: Int): Token {
        return Companion.deserialize(data, publicKey, offset)
    }

    fun receiveContent(content: ByteArray): Boolean {
        contentHash = sha3_256(content)
        if (this.contentHash == contentHash) {
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

    companion object {
        fun deserialize(data: ByteArray, publicKey: PublicKey, offset: Int = 0): Token {
            val sigLength = publicKey.getSignatureLength()
            return Token(data.copyOfRange(offset, offset + 32),
                data.copyOfRange(offset + 32, offset + 64),
                data.copyOfRange(offset + 64, offset + 64 + sigLength))
        }

        fun create(previousToken: Token, content: ByteArray, privateKey: PrivateKey): Token {
            return Token(previousToken.hash, content, privateKey = privateKey)
        }

        fun fromDatabaseTuple(
            previousTokenHash: ByteArray,
            signature: ByteArray,
            contentHash: ByteArray,
            content: ByteArray?
        ): Token {
            val token = Token(previousTokenHash, signature, contentHash = contentHash)
            if (content != null) {
                token.receiveContent(content)
            }
            return token
        }
    }


}
