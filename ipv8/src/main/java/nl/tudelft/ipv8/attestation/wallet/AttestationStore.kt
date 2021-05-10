package nl.tudelft.ipv8.attestation.wallet

import nl.tudelft.ipv8.attestation.WalletAttestation
import nl.tudelft.ipv8.attestation.wallet.cryptography.bonehexact.BonehPrivateKey
import nl.tudelft.ipv8.keyvault.PublicKey

class AttestationBlob(
    val attestationHash: ByteArray,
    val blob: ByteArray,
    val key: ByteArray,
    val idFormat: String,
    val value: ByteArray?,
)

interface AttestationStore {
    fun getAllAttestations(): List<AttestationBlob>

    fun insertAttestation(
        attestation: WalletAttestation,
        attestationHash: ByteArray,
        privateKey: BonehPrivateKey,
        idFormat: String,
        value: ByteArray? = null,
    )

    fun getAttestationBlobByHash(attestationHash: ByteArray): ByteArray?

    fun getValueByHash(attestationHash: ByteArray): ByteArray?

    fun deleteAttestationByHash(attestationHash: ByteArray)
}
