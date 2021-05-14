package nl.tudelft.ipv8.attestation.wallet.cryptography

import nl.tudelft.ipv8.attestation.wallet.cryptography.bonehexact.BonehPrivateKey
import nl.tudelft.ipv8.attestation.wallet.cryptography.bonehexact.BonehPublicKey
import nl.tudelft.ipv8.util.sha1

// TODO: Create WalletAttestationKey superclass
abstract class WalletAttestation {

    abstract val publicKey: BonehPublicKey
    abstract val idFormat: String?

    abstract fun serialize(): ByteArray

    abstract fun deserialize(
        serialized: ByteArray,
        idFormat: String,
    ): WalletAttestation

    abstract fun serializePrivate(publicKey: BonehPublicKey): ByteArray

    abstract fun deserializePrivate(
        privateKey: BonehPrivateKey,
        serialized: ByteArray,
        idFormat: String?,
    ): WalletAttestation

    fun getHash(): ByteArray {
        return sha1(this.serialize())
    }

    override fun toString(): String {
        return "WalletAttestation(publicKey=$publicKey, idFormat=$idFormat)"
    }

}
