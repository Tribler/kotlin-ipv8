package nl.tudelft.ipv8.attestation.revocation

import nl.tudelft.ipv8.attestation.Authority
import nl.tudelft.ipv8.attestation.Revocations
import nl.tudelft.ipv8.keyvault.PublicKey
import nl.tudelft.ipv8.sqldelight.GetAllRevocations

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

    fun getRevocations(publicKeyHash: ByteArray, versions: List<Long>): List<RevocationBlob>

    fun getVersionsSince(publicKeyHash: ByteArray, sinceVersion: Long): List<Long>

    fun getAllRevocations(): List<GetAllRevocations>
}
