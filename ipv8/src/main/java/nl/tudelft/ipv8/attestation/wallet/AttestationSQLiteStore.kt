package nl.tudelft.ipv8.attestation.wallet

import nl.tudelft.ipv8.attestation.attestation.WalletAttestation
import nl.tudelft.ipv8.sqldelight.Database
import javax.crypto.SecretKey

class AttestationSQLiteStore(database: Database) : AttestationStore {
    private val dao = database.dbBlockQueries

    override fun getAttestationByHash(attestationHash: ByteArray): ByteArray {
        dao.get
    }

    override fun getAll(): List<ByteArray> {
        TODO("Not yet implemented")
    }

    override fun insertAttestation(
        attestation: WalletAttestation,
        attestationHash: ByteArray,
        secretKey: SecretKey,
        idFormat: String,
    ) {
        TODO("Not yet implemented")
    }
}
