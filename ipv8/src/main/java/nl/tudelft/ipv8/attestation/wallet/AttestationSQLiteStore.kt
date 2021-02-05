package nl.tudelft.ipv8.attestation.wallet

import nl.tudelft.ipv8.attestation.WalletAttestation
import nl.tudelft.ipv8.attestation.wallet.cryptography.bonehexact.BonehPrivateKey
import nl.tudelft.ipv8.sqldelight.Database


private val attestationMapper: (
    ByteArray,
    ByteArray,
    ByteArray,
    String,
) -> AttestationBlob = { hash, blob, key, id_format ->
    AttestationBlob(
        hash, blob, key, id_format
    )
}


class AttestationSQLiteStore(database: Database) : AttestationStore {
    private val dao = database.dbAttestationQueries

    override fun getAttestationByHash(attestationHash: ByteArray): ByteArray? {
        return dao.getAttestationByHash(attestationHash).executeAsOneOrNull()
    }

    override fun getAll(): List<AttestationBlob> {
        return dao.getAll(attestationMapper).executeAsList()
    }

    override fun insertAttestation(
        attestation: WalletAttestation,
        attestationHash: ByteArray,
        secretKey: BonehPrivateKey,
        idFormat: String,
    ) {
        val blob = attestation.serializePrivate(secretKey.publicKey())
        dao.insertAttestation(attestationHash, blob, secretKey.serialize(), idFormat)
    }
}
