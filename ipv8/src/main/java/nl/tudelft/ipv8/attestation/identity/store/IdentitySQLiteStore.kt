package nl.tudelft.ipv8.attestation.identity.store

import nl.tudelft.ipv8.attestation.identity.datastructures.IdentityAttestation
import nl.tudelft.ipv8.attestation.identity.datastructures.Metadata
import nl.tudelft.ipv8.attestation.identity.datastructures.tokenTree.Token
import nl.tudelft.ipv8.keyvault.PublicKey
import nl.tudelft.ipv8.keyvault.defaultCryptoProvider
import nl.tudelft.ipv8.sqldelight.Database

private val tokenMapper: (
    ByteArray,
    ByteArray,
    ByteArray,
    ByteArray?,
) -> Token = { previousTokenHash, signature, contentHash, content ->
    Token.fromDatabaseTuple(previousTokenHash, signature, contentHash, content)
}

private val metadataMapper: (
    ByteArray,
    ByteArray,
    ByteArray,
) -> Metadata = { tokenPointer, signature, serializedMetadata ->
    Metadata.fromDatabaseTuple(tokenPointer, signature, serializedMetadata)
}

private val identityAttestationMapper: (
    ByteArray,
    ByteArray?,
) -> IdentityAttestation = { metadataPointer, signature ->
    IdentityAttestation.fromDatabaseTuple(metadataPointer, signature)
}

class IdentitySQLiteStore(database: Database) : IdentityStore {
    private val dao = database.dbIdentityQueries

    override fun insertToken(publicKey: PublicKey, token: Token) {
        val (previousTokenHash, signature, contentHash, content) = token
        dao.insertToken(publicKey.keyToBin(), previousTokenHash, signature, contentHash, content)
    }

    override fun insertMetadata(publicKey: PublicKey, metadata: Metadata) {
        val (tokenPointer, signature, serializedMetadata) = metadata.toDatabaseTuple()
        dao.insertMetadata(publicKey.keyToBin(), tokenPointer, signature, serializedMetadata)
    }

    override fun insertAttestation(
        publicKey: PublicKey,
        authorityKey: PublicKey,
        attestation: IdentityAttestation
    ) {
        val (metadataPointer, signature) = attestation.toDatabaseTuple()
        dao.insertIdentityAttestation(
            publicKey.keyToBin(),
            authorityKey.keyToBin(),
            metadataPointer,
            signature
        )
    }

    override fun getTokensFor(publicKey: PublicKey): List<Token> {
        return dao.getTokensFor(publicKey.keyToBin(), tokenMapper).executeAsList()
    }

    override fun getMetadataFor(publicKey: PublicKey): List<Metadata> {
        return dao.getMetadataFor(publicKey.keyToBin(), metadataMapper).executeAsList()
    }

    override fun getAttestationsFor(publicKey: PublicKey): Set<IdentityAttestation> {
        return dao.getAttestationsFor(publicKey.keyToBin(), identityAttestationMapper)
            .executeAsList().toSet()
    }

    override fun getAttestationsBy(publicKey: PublicKey): Set<IdentityAttestation> {
        return dao.getAttestationsBy(publicKey.keyToBin(), identityAttestationMapper)
            .executeAsList().toSet()
    }

    override fun getAttestationsOver(metadata: Metadata): Set<IdentityAttestation> {
        return dao.getAttestationsOver(metadata.hash, identityAttestationMapper).executeAsList()
            .toSet()
    }

    override fun getAuthority(attestation: IdentityAttestation): ByteArray {
        return dao.getAuthority(attestation.signature).executeAsOne()
    }

    override fun getCredentialOver(metadata: Metadata): Credential {
        return Credential(metadata, this.getAttestationsOver(metadata))
    }

    override fun getCredentialsFor(publicKey: PublicKey): List<Credential> {
        return this.getMetadataFor(publicKey).map { Credential(it, this.getAttestationsOver(it)) }
    }

    override fun getKnownIdentities(): List<PublicKey> {
        return dao.getKnownIdentities().executeAsList()
            .map { defaultCryptoProvider.keyFromPublicBin(it) }
    }

    override fun getKnownSubjects(): List<PublicKey> {
        return dao.getKnownSubjects().executeAsList()
            .map { defaultCryptoProvider.keyFromPublicBin(it) }
    }

    override fun dropIdentityTable(publicKey: PublicKey): List<ByteArray> {
        val attestationHashes = this.getTokensFor(publicKey).map { it.contentHash }
        val keyBin = publicKey.keyToBin()
        dao.deleteTokensFor(keyBin)
        dao.deleteMetadataFor(keyBin)
        dao.deleteIdentityAttestationsFor(keyBin)
        return attestationHashes
    }
}
