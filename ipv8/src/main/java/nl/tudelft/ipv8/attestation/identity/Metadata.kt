package nl.tudelft.ipv8.attestation.identity

import nl.tudelft.ipv8.attestation.SignedObject
import nl.tudelft.ipv8.keyvault.PrivateKey
import nl.tudelft.ipv8.keyvault.PublicKey
import nl.tudelft.ipv8.util.toHex
import org.json.JSONObject

class Metadata(
    private val tokenPointer: ByteArray,
    private val serializedJSONObject: ByteArray,
    privateKey: PrivateKey? = null,
    signature: ByteArray? = null
) : SignedObject(privateKey, signature) {


    override fun getPlaintext(): ByteArray {
        return this.tokenPointer + this.serializedJSONObject
    }

    fun toDatabaseTuple(): Array<ByteArray> {
        return arrayOf(this.tokenPointer, this.signature, this.serializedJSONObject)
    }

    override fun toString(): String {
        return "Metadata(${this.tokenPointer.toHex()},\n${this.serializedJSONObject.toString(Charsets.UTF_8)}"
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
            val signIndex = data.lastIndex - publicKey.getSignatureLength()
            return Metadata(data.copyOfRange(0, 32),
                data.copyOfRange(32, signIndex),
                signature = data.copyOfRange(signIndex, data.size))
        }

        fun create(token: Token, jsonObject: JSONObject, privateKey: PrivateKey): Metadata {
            return Metadata(token.hash,
                jsonObject.toString().toByteArray(),
                privateKey = privateKey)
        }

        fun fromDatabaseTuple(
            tokenPointer: ByteArray,
            signature: ByteArray,
            serializedJSONObject: ByteArray
        ): Metadata {
            return Metadata(tokenPointer, serializedJSONObject, signature = signature)
        }
    }


}
