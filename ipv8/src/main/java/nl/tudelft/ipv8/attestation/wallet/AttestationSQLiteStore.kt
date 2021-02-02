package nl.tudelft.ipv8.attestation.wallet

import nl.tudelft.ipv8.attestation.WalletAttestation
import nl.tudelft.ipv8.keyvault.PrivateKey
import nl.tudelft.ipv8.sqldelight.Database

class AttestationSQLiteStore(database: Database) : AttestationStore {
    private val dao = database.dbBlockQueries

    override fun getAttestationByHash(attestationHash: ByteArray): ByteArray {
        TODO()
    }

    override fun getAll(): List<ByteArray> {
        TODO("Not yet implemented")
    }

    override fun insertAttestation(
        attestation: WalletAttestation,
        attestationHash: ByteArray,
        secretKey: PrivateKey,
        idFormat: String,
    ) {
        TODO("Not yet implemented")
    }
}
