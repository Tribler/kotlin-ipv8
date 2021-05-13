package nl.tudelft.ipv8.attestation.revocation

import mu.KotlinLogging
import nl.tudelft.ipv8.attestation.common.Authority
import nl.tudelft.ipv8.keyvault.PublicKey
import nl.tudelft.ipv8.keyvault.defaultCryptoProvider
import nl.tudelft.ipv8.sqldelight.Database

private val authorityMapper: (
    ByteArray?,
    ByteArray,
    Long?,
    Long?,
) -> Authority = { public_key, hash, version, recognized ->
    Authority(
        public_key?.let { defaultCryptoProvider.keyFromPublicBin(it) },
        hash,
        version ?: 0L,
        recognized?.toInt() == 1
    )
}

private val logger = KotlinLogging.logger {}

class AuthoritySQLiteStore(database: Database) : AuthorityStore {
    private val dao = database.dbAuthorityQueries

    override fun getKnownAuthorities(): List<Authority> {
        return dao.getAllAuthorities(authorityMapper).executeAsList()
    }

    override fun getRecognizedAuthorities(): List<Authority> {
        return dao.getAllRecognizedAuthorities(authorityMapper).executeAsList()
    }

    override fun getAuthorityByHash(hash: ByteArray): Authority? {
        return dao.getAuthorityByHash(hash, authorityMapper).executeAsOneOrNull()
    }

    override fun recognizeAuthority(hash: ByteArray) {
        return dao.recognizeAuthority(hash)
    }

    override fun disregardAuthority(hash: ByteArray) {
        return dao.disregardAuthority(hash)
    }

    override fun insertAuthority(publicKey: PublicKey) {
        return dao.insertAuthority(publicKey.keyToBin(), publicKey.keyToHash(), null, null)
    }

    override fun insertTrustedAuthority(publicKey: PublicKey) {
        return dao.insertAuthority(publicKey.keyToBin(), publicKey.keyToHash(), null, 1)
    }

    override fun insertAuthority(hash: ByteArray) {
        return dao.insertAuthority(null, hash, null, null)
    }

    override fun insertRevocations(
        publicKeyHash: ByteArray,
        version: Long,
        signature: ByteArray,
        revokedHashes: List<ByteArray>,
    ) {
        var authorityId = dao.getAuthorityIdByHash(publicKeyHash).executeAsOneOrNull()
        if (authorityId == null) {
            this.insertAuthority(publicKeyHash)
            authorityId = dao.getAuthorityIdByHash(publicKeyHash).executeAsOne()
        }

        var versionId =
            dao.getVersionByAuthorityIDandVersionNumber(authorityId, version)
                .executeAsOneOrNull()?.version_id
        if (versionId == null) {
            dao.insertVersion(authorityId, version, signature)
            versionId = dao.getVersionByAuthorityIDandVersionNumber(authorityId, version)
                .executeAsOne().version_id
        }

        revokedHashes.forEach { dao.insertRevocation(authorityId, versionId, it) }
        dao.updateVersionFor(versionId, publicKeyHash)
    }

    override fun getVersionsSince(publicKeyHash: ByteArray, sinceVersion: Long): List<Long> {
        val authorityId = dao.getAuthorityIdByHash(publicKeyHash).executeAsOneOrNull()
        return if (authorityId == null) {
            emptyList()
        } else
            dao.getVersionsSince(authorityId, sinceVersion).executeAsList()
    }

    override fun getRevocations(
        publicKeyHash: ByteArray,
        versions: List<Long>,
    ): List<RevocationBlob> {
        val authorityId = dao.getAuthorityIdByHash(publicKeyHash).executeAsOne()
        val versionEntries =
            dao.getVersionsByAuthorityIDandVersionNumbers(authorityId, versions).executeAsList()

        return versionEntries.map {
            RevocationBlob(
                publicKeyHash, it.version_number,
                it.signature,
                dao.getRevocationsByAuthorityIdAndVersionId(authorityId, it.version_id)
                    .executeAsList()
            )
        }
    }

    override fun getAllRevocations(): List<ByteArray> {
        return dao.getRevocations().executeAsList()
    }

    override fun getNumberOfRevocations(): Long {
        return dao.getNumberOfRevocations().executeAsOne()
    }

    override fun getMissingVersion(authorityKeyHash: ByteArray): Long? {
        val authorityId = dao.getAuthorityIdByHash(authorityKeyHash).executeAsOneOrNull()
        return authorityId?.let { dao.getMissingVersionByAuthorityID(it).executeAsOneOrNull()?.MIN }
    }

    override fun isRevoked(signature: ByteArray): Boolean {
        return dao.isRevoked(signature).executeAsList().isNotEmpty()
    }

    override fun isRevokedBy(signature: ByteArray, authorityKeyHash: ByteArray): Boolean {
        val authorityId = dao.getAuthorityIdByHash(authorityKeyHash).executeAsOneOrNull()
        return if (authorityId == null) {
            false
        } else {
            dao.isRevokedBy(signature, authorityId).executeAsList().isNotEmpty()
        }
    }
}
