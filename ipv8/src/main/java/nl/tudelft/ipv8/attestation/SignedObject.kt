package nl.tudelft.ipv8.attestation

import nl.tudelft.ipv8.keyvault.PrivateKey
import nl.tudelft.ipv8.keyvault.PublicKey
import nl.tudelft.ipv8.keyvault.defaultCryptoProvider
import nl.tudelft.ipv8.messaging.deserializeULong
import nl.tudelft.ipv8.util.sha3_256

abstract class SignedObject(val privateKey: PrivateKey? = null, signature: ByteArray? = null) {

    var hash = byteArrayOf()
    open lateinit var signature: ByteArray
    private val crypto = defaultCryptoProvider


    init {
        this.sign(privateKey, signature)
    }

    fun verify(publicKey: PublicKey): Boolean {
        return publicKey.verify(signature, this.getPlaintext())
    }

    private fun sign(privateKey: PrivateKey? = null, signature: ByteArray? = null) {
        if (privateKey != null && signature == null) {
            privateKey.sign(this.getPlaintext())
        } else if (privateKey == null && signature != null) {
            this.signature = signature
        } else {
            throw RuntimeException("Specify either a private key or a signature.")
        }

        this.hash = sha3_256(this.getPlaintextSigned())

    }

    abstract fun getPlaintext(): ByteArray

    fun getPlaintextSigned(): ByteArray {
        return this.getPlaintext() + this.signature
    }

    abstract fun deserialize(data: ByteArray, publicKey: PublicKey, offset: Int = 0): SignedObject

    override fun equals(other: Any?): Boolean {
        if (other !is SignedObject){
            return false
        }
        return this.getPlaintextSigned().contentEquals(other.getPlaintextSigned())
    }

    override fun hashCode(): Int {
        // TODO: verify that this is correct.
        return deserializeULong(this.hash).toInt()
    }
}
