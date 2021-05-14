package nl.tudelft.ipv8.attestation.wallet.store

import nl.tudelft.ipv8.attestation.wallet.cryptography.WalletAttestation
import nl.tudelft.ipv8.attestation.wallet.cryptography.bonehexact.BonehPrivateKey
import nl.tudelft.ipv8.sqldelight.Database

private val attestationMapper: (
    ByteArray,
    ByteArray,
    ByteArray,
    String,
    ByteArray?,
) -> AttestationBlob = { hash, blob, key, id_format, value ->
    AttestationBlob(
        hash,
        blob,
        key,
        id_format,
        value
    )
}

class AttestationSQLiteStore(database: Database) : AttestationStore {
    private val dao = database.dbAttestationQueries

    override fun getAllAttestations(): List<AttestationBlob> {
        return dao.getAllAttestations(attestationMapper).executeAsList()
    }

    override fun insertAttestation(
        attestation: WalletAttestation,
        attestationHash: ByteArray,
        privateKey: BonehPrivateKey,
        idFormat: String,
        value: ByteArray?,
    ) {
        val blob = attestation.serializePrivate(privateKey.publicKey())
        dao.insertAttestation(
            attestationHash,
            blob,
            privateKey.serialize(),
            idFormat,
            value
        )
    }

    override fun getAttestationBlobByHash(attestationHash: ByteArray): ByteArray? {
        return dao.getAttestationByHash(attestationHash).executeAsOneOrNull()
    }

    override fun getValueByHash(attestationHash: ByteArray): ByteArray? {
        return dao.getValueByHash(attestationHash).executeAsOneOrNull()?.value
    }

    override fun deleteAttestationByHash(attestationHash: ByteArray) {
        return dao.deleteAttestationByHash(attestationHash)
    }

    override fun deleteAttestations(attestationHashes: List<ByteArray>) {
        return dao.deleteAttestations(attestationHashes)
    }
}
