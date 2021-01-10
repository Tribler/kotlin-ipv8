package nl.tudelft.ipv8.attestation.identity

import nl.tudelft.ipv8.attestation.attestation.SignedObject
import nl.tudelft.ipv8.keyvault.PrivateKey
import nl.tudelft.ipv8.keyvault.PublicKey
import nl.tudelft.ipv8.util.toHex

class Attestation(
    private val metadataPointer: ByteArray,
    privateKey: PrivateKey? = null,
    signature: ByteArray? = null
) : SignedObject(privateKey, signature) {

    override fun getPlaintext(): ByteArray {
        return this.metadataPointer
    }

    fun toDatabaseTuple(): Pair<ByteArray, ByteArray> {
        return Pair(this.metadataPointer, signature)
    }

    override fun toString(): String {
        return "Attestation${metadataPointer.toHex()})"
    }

    companion object {
        fun deserialize(data: ByteArray, publicKey: PublicKey, offset: Int = 0): Attestation {
            val signLength = publicKey.getSignatureLength()
            return Attestation(data.copyOfRange(offset, offset + 32),
                signature = data.copyOfRange(offset + 32, offset + 32 + signLength))
        }

        fun create(metadata: Metadata, privateKey: PrivateKey): Attestation {
            return Attestation(metadata.hash, privateKey = privateKey)
        }

        fun fromDatabaseTuple(metadataPointer: ByteArray, signature: ByteArray): Attestation {
            return Attestation(metadataPointer, signature = signature)
        }
    }


}
