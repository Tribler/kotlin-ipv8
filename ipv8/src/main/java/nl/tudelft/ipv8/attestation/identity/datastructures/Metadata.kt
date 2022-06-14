package nl.tudelft.ipv8.attestation.identity.datastructures

import nl.tudelft.ipv8.attestation.identity.datastructures.tokenTree.Token
import nl.tudelft.ipv8.keyvault.PrivateKey
import nl.tudelft.ipv8.keyvault.PublicKey
import nl.tudelft.ipv8.util.toHex
import org.json.JSONObject

class Metadata(
    val tokenPointer: ByteArray,
    val serializedMetadata: ByteArray,
    privateKey: PrivateKey? = null,
    signature: ByteArray? = null,
) : SignedObject(privateKey, signature) {

    init {
        super.init()
    }

    override fun getPlaintext(): ByteArray {
        return this.tokenPointer + this.serializedMetadata
    }

    fun toDatabaseTuple(): Triple<ByteArray, ByteArray, ByteArray> {
        return Triple(this.tokenPointer, this.signature, this.serializedMetadata)
    }

    override fun toString(): String {
        return "Metadata(${this.tokenPointer.toHex()},\n${String(this.serializedMetadata)}"
    }

    override fun deserialize(data: ByteArray, publicKey: PublicKey, offset: Int): Metadata {
        return Companion.deserialize(data, publicKey, offset)
    }

    companion object {
        fun deserialize(data: ByteArray, publicKey: PublicKey, offset: Int? = 0): Metadata {
            if (offset != 0) {
                throw RuntimeException("Offset is unsupported for Metadata.")
            }

            // Index on which signature start.
            val signIndex = data.size - publicKey.getSignatureLength()
            return Metadata(
                data.copyOfRange(0, 32),
                data.copyOfRange(32, signIndex),
                signature = data.copyOfRange(signIndex, data.size)
            )
        }

        fun create(token: Token, jsonObject: JSONObject, privateKey: PrivateKey): Metadata {
            return Metadata(
                token.hash,
                jsonObject.toString().toByteArray(),
                privateKey = privateKey
            )
        }

        fun fromDatabaseTuple(
            tokenPointer: ByteArray,
            signature: ByteArray?,
            serializedMetadata: ByteArray,
        ): Metadata {
            return Metadata(tokenPointer, serializedMetadata, signature = signature)
        }
    }
}
