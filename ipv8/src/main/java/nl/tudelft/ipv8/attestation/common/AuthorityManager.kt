package nl.tudelft.ipv8.attestation.common

import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import mu.KotlinLogging
import nl.tudelft.ipv8.attestation.revocation.AuthorityStore
import nl.tudelft.ipv8.attestation.revocation.RevocationBlob
import nl.tudelft.ipv8.attestation.revocation.datastructures.BloomFilter
import nl.tudelft.ipv8.keyvault.PublicKey
import nl.tudelft.ipv8.util.ByteArrayKey
import nl.tudelft.ipv8.util.toKey

class Authority(
    val publicKey: PublicKey?,
    val hash: ByteArray,
    var version: Long = 0L,
    val recognized: Boolean = false,
)

const val DEFAULT_CAPACITY = 100
private val logger = KotlinLogging.logger {}

class AuthorityManager(val authorityDatabase: AuthorityStore) {

    private val trustedAuthorities = hashMapOf<ByteArrayKey, Authority>()
    private val revocations: BloomFilter
    private val lock = Object()

    init {
        loadTrustedAuthorities()
        val revocationsAmount = authorityDatabase.getNumberOfRevocations()
        if (revocationsAmount + 100 >= Integer.MAX_VALUE) {
            logger.error("Number of revocations overflows bloom filter capacity.")
        }
        revocations = BloomFilter(revocationsAmount.toInt() + DEFAULT_CAPACITY)
        GlobalScope.launch {
            authorityDatabase.getAllRevocations().forEach { revocations.add(it) }
        }
    }

    fun loadTrustedAuthorities() {
        val authorities = authorityDatabase.getRecognizedAuthorities()
        synchronized(lock) {
            authorities.forEach {
                trustedAuthorities[it.hash.toKey()] = it
            }
        }
    }

    fun verify(signature: ByteArray): Boolean {
        return !(this.revocations.probablyContains(signature) && authorityDatabase.isRevoked(
            signature
        ))
    }

    fun verify(signature: ByteArray, authorityKeyHash: ByteArray): Boolean {
        return !(this.revocations.probablyContains(signature) && authorityDatabase.isRevokedBy(
            signature,
            authorityKeyHash
        ))
    }

    fun insertRevocations(
        publicKeyHash: ByteArray,
        versionNumber: Long,
        signature: ByteArray,
        revokedHashes: List<ByteArray>
    ) {
        authorityDatabase.insertRevocations(publicKeyHash, versionNumber, signature, revokedHashes)
        if (this.trustedAuthorities.containsKey(publicKeyHash.toKey())) {
            this.trustedAuthorities[publicKeyHash.toKey()]!!.version = versionNumber
        }
        revokedHashes.forEach { revocations.add(it) }
    }

    /**
     * Method for fetching all latest known versions.
     */
    fun getLatestRevocationPreviews(): Map<ByteArrayKey, Long> {
        val authorities = authorityDatabase.getKnownAuthorities()
        val localRefs = hashMapOf<ByteArrayKey, Long>()
        authorities.forEach {
            if (it.version > 0)
                localRefs[it.hash.toKey()] = it.version
        }
        return localRefs
    }

    /**
     * Method for fetching the lowest missing versions.
     */
    fun getMissingRevocationPreviews(): Map<ByteArrayKey, Long> {
        val authorities = authorityDatabase.getKnownAuthorities()
        val localRefs = hashMapOf<ByteArrayKey, Long>()
        authorities.forEach {
            localRefs[it.hash.toKey()] =
                it.version.coerceAtMost(
                    authorityDatabase.getMissingVersion(it.hash) ?: Long.MAX_VALUE
                )
        }
        return localRefs
    }

    fun getRevocations(publicKeyHash: ByteArray, fromVersion: Long = 0): List<RevocationBlob> {
        val versions = authorityDatabase.getVersionsSince(publicKeyHash, fromVersion)
        return authorityDatabase.getRevocations(publicKeyHash, versions)
    }

    fun getAllRevocations(): List<ByteArray> {
        return authorityDatabase.getAllRevocations()
    }

    fun loadDefaultAuthorities() {
        TODO("Preinstalled Authorities yet to be designed.")
    }

    fun getAuthorities(): List<Authority> {
        return this.authorityDatabase.getKnownAuthorities()
    }

    fun getTrustedAuthorities(): List<Authority> {
        return this.trustedAuthorities.values.toList()
    }

    fun addTrustedAuthority(publicKey: PublicKey) {
        val hash = publicKey.keyToHash()
        if (!this.containsAuthority(hash)) {
            val localAuthority = authorityDatabase.getAuthorityByHash(hash)
            if (localAuthority == null) {
                authorityDatabase.insertTrustedAuthority(publicKey)
                synchronized(lock) {
                    this.trustedAuthorities[hash.toKey()] = Authority(publicKey, hash)
                }
            } else {
                authorityDatabase.recognizeAuthority(publicKey.keyToHash())
                synchronized(lock) {
                    this.trustedAuthorities[hash.toKey()] = localAuthority
                }
            }
        }
    }

    fun deleteTrustedAuthority(hash: ByteArray) {
        if (this.containsAuthority(hash)) {
            this.trustedAuthorities.remove(hash.toKey())
            this.authorityDatabase.disregardAuthority(hash)
        }
    }

    fun getTrustedAuthority(hash: ByteArray): Authority? {
        return this.trustedAuthorities[hash.toKey()]
    }

    fun getAuthority(hash: ByteArray): Authority? {
        return this.trustedAuthorities[hash.toKey()] ?: authorityDatabase.getAuthorityByHash(
            hash
        )
    }

    fun deleteTrustedAuthority(publicKey: PublicKey) {
        return this.deleteTrustedAuthority(publicKey.keyToHash())
    }

    fun containsAuthority(hash: ByteArray): Boolean {
        return this.trustedAuthorities.containsKey(hash.toKey())
    }
}
