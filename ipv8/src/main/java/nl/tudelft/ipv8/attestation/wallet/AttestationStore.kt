package nl.tudelft.ipv8.attestation.wallet

import nl.tudelft.ipv8.attestation.Authority
import nl.tudelft.ipv8.attestation.WalletAttestation
import nl.tudelft.ipv8.attestation.wallet.cryptography.bonehexact.BonehPrivateKey
import nl.tudelft.ipv8.keyvault.PublicKey

class AttestationBlob(
    val attestationHash: ByteArray,
    val blob: ByteArray,
    val key: ByteArray,
    val idFormat: String,
    val metadata: String?,
    val signature: ByteArray?,
    val attestorKey: PublicKey?,
)

interface AttestationStore {
    fun getAllAttestations(): List<AttestationBlob>

    fun insertAttestation(
        attestation: WalletAttestation,
        attestationHash: ByteArray,
        privateKey: BonehPrivateKey,
        idFormat: String,
        metadata: String? = null,
        signature: ByteArray? = null,
        attestorKey: PublicKey? = null,
    )

    fun getAttestationByHash(attestationHash: ByteArray): ByteArray?

    fun deleteAttestationByHash(attestationHash: ByteArray)

    fun getAllAuthorities(): List<Authority>

    fun insertAuthority(publicKey: PublicKey, hash: String)

    fun getAuthorityByPublicKey(publicKey: PublicKey): Authority?

    fun getAuthorityByHash(hash: String): Authority?

    fun deleteAuthorityByHash(hash: String)
}
