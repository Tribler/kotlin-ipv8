package nl.tudelft.ipv8.attestation.revocation

import nl.tudelft.ipv8.attestation.common.Authority
import nl.tudelft.ipv8.keyvault.PublicKey

class RevocationBlob(
    val publicKeyHash: ByteArray,
    val version: Long,
    val signature: ByteArray,
    val revocations: List<ByteArray>
)

interface AuthorityStore {

    fun getKnownAuthorities(): List<Authority>
    fun getRecognizedAuthorities(): List<Authority>
    fun getAuthorityByHash(hash: ByteArray): Authority?
    fun recognizeAuthority(hash: ByteArray)
    fun disregardAuthority(hash: ByteArray)
    fun insertTrustedAuthority(publicKey: PublicKey)
    fun insertAuthority(publicKey: PublicKey)
    fun insertAuthority(hash: ByteArray)
    fun insertRevocations(
        publicKeyHash: ByteArray,
        version: Long,
        signature: ByteArray,
        revokedHashes: List<ByteArray>,
    )

    fun isRevoked(signature: ByteArray): Boolean
    fun isRevokedBy(signature: ByteArray, authorityKeyHash: ByteArray): Boolean
    fun getRevocations(publicKeyHash: ByteArray, versions: List<Long>): List<RevocationBlob>
    fun getVersionsSince(publicKeyHash: ByteArray, sinceVersion: Long): List<Long>
    fun getAllRevocations(): List<ByteArray>
    fun getNumberOfRevocations(): Long
    fun getMissingVersion(authorityKeyHash: ByteArray): Long?
}
