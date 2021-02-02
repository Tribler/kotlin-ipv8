package nl.tudelft.ipv8.attestation.wallet

import nl.tudelft.ipv8.attestation.WalletAttestation
import nl.tudelft.ipv8.keyvault.PrivateKey

interface AttestationStore {
    fun getAttestationByHash(attestationHash: ByteArray): ByteArray
    fun getAll(): List<ByteArray>
    fun insertAttestation(
        attestation: WalletAttestation,
        attestationHash: ByteArray,
        privateKey: PrivateKey,
        idFormat: String,
    )
}
