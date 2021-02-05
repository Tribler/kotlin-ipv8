package nl.tudelft.ipv8.attestation.wallet

import nl.tudelft.ipv8.attestation.WalletAttestation
import nl.tudelft.ipv8.attestation.wallet.cryptography.bonehexact.BonehPrivateKey

class AttestationBlob(val attestationHash: ByteArray, val blob: ByteArray, val key: ByteArray, val idFormat: String) {

}

interface AttestationStore {
    fun getAttestationByHash(attestationHash: ByteArray): ByteArray?
    fun getAll(): List<AttestationBlob>
    fun insertAttestation(
        attestation: WalletAttestation,
        attestationHash: ByteArray,
        privateKey: BonehPrivateKey,
        idFormat: String,
    )
}
