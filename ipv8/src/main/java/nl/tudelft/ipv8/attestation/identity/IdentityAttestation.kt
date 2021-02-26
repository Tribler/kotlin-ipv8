package nl.tudelft.ipv8.attestation.identity

import nl.tudelft.ipv8.attestation.SignedObject
import nl.tudelft.ipv8.keyvault.PrivateKey
import nl.tudelft.ipv8.keyvault.PublicKey
import nl.tudelft.ipv8.util.toHex

class IdentityAttestation(
    private val metadataPointer: ByteArray,
    privateKey: PrivateKey? = null,
    signature: ByteArray? = null,
) : SignedObject(privateKey, signature) {

    override fun getPlaintext(): ByteArray {
        return this.metadataPointer
    }

    fun toDatabaseTuple(): Pair<ByteArray, ByteArray> {
        return Pair(this.metadataPointer, this.signature)
    }

    override fun toString(): String {
        return "Attestation${metadataPointer.toHex()})"
    }


    override fun deserialize(data: ByteArray, publicKey: PublicKey, offset: Int): IdentityAttestation {
        return Companion.deserialize(data, publicKey, offset)
    }

    companion object {
        fun deserialize(data: ByteArray, publicKey: PublicKey, offset: Int = 0): IdentityAttestation {
            val signLength = publicKey.getSignatureLength()
            return IdentityAttestation(data.copyOfRange(offset, offset + 32),
                signature = data.copyOfRange(offset + 32, offset + 32 + signLength))
        }

        fun create(metadata: Metadata, privateKey: PrivateKey): IdentityAttestation {
            return IdentityAttestation(metadata.hash, privateKey = privateKey)
        }

        fun fromDatabaseTuple(metadataPointer: ByteArray, signature: ByteArray): IdentityAttestation {
            return IdentityAttestation(metadataPointer, signature = signature)
        }
    }


}
