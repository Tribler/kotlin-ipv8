package nl.tudelft.ipv8.attestation.wallet

import mu.KotlinLogging
import nl.tudelft.ipv8.attestation.WalletAttestation
import nl.tudelft.ipv8.attestation.wallet.cryptography.bonehexact.BonehPrivateKey
import nl.tudelft.ipv8.keyvault.PublicKey
import nl.tudelft.ipv8.keyvault.defaultCryptoProvider
import nl.tudelft.ipv8.sqldelight.Database

private val attestationMapper: (
    ByteArray,
    ByteArray,
    ByteArray,
    String,
    String?,
    ByteArray?,
    ByteArray?,
) -> AttestationBlob = { hash, blob, key, id_format, value, signature, attestor_key ->
    AttestationBlob(
        hash,
        blob,
        key,
        id_format,
        value,
        signature,
        attestor_key?.let { defaultCryptoProvider.keyFromPublicBin(it) }
    )
}

private val logger = KotlinLogging.logger {}

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
        value: String?,
        signature: ByteArray?,
        attestorKey: PublicKey?,
    ) {
        val blob = attestation.serializePrivate(privateKey.publicKey())
        dao.insertAttestation(
            attestationHash,
            blob,
            privateKey.serialize(),
            idFormat,
            value,
            signature,
            attestorKey?.keyToBin()
        )
    }

    override fun getAttestationBlobByHash(attestationHash: ByteArray): ByteArray? {
        return dao.getAttestationByHash(attestationHash).executeAsOneOrNull()
    }

    override fun getValueAndSignatureByHash(attestationHash: ByteArray): Pair<String, ByteArray>? {
        val pair =
            dao.getValueAndSignatureByHash(attestationHash).executeAsOneOrNull() ?: return null
        return Pair(pair.value!!, pair.signature!!)
    }

    override fun deleteAttestationByHash(attestationHash: ByteArray) {
        return dao.deleteAttestationByHash(attestationHash)
    }
}
