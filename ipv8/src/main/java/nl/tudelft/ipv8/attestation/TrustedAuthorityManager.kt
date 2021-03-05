package nl.tudelft.ipv8.attestation

import nl.tudelft.ipv8.attestation.wallet.AttestationStore
import nl.tudelft.ipv8.keyvault.PublicKey
import nl.tudelft.ipv8.util.toHex

class Authority(
    val publicKey: PublicKey,
    val hash: String,
)

class TrustedAuthorityManager(private val database: AttestationStore) {

    private val trustedAuthorities = hashMapOf<String, Authority>()
    private val lock = Object()

    fun loadAuthorities() {
        val authorities = database.getAllAuthorities()
        synchronized(lock) {
            authorities.forEach {
                trustedAuthorities[it.hash] = Authority(it.publicKey, it.hash)
            }
        }
    }

    fun loadDefaultAuthorities() {
        TODO("Preinstalled Authorities yet to be designed.")
    }

    fun getAuthorities(): List<Authority> {
        return this.database.getAllAuthorities()
    }

    fun addTrustedAuthority(publicKey: PublicKey) {
        val hash = publicKey.keyToHash().toHex()
        if (!this.contains(hash)) {
            database.insertAuthority(publicKey, hash)
            synchronized(lock) {
                this.trustedAuthorities[hash] = Authority(publicKey, hash)
            }
        }
    }

    fun deleteTrustedAuthority(hash: String) {
        if (this.contains(hash)) {
            this.trustedAuthorities.remove(hash)
            this.database.deleteAuthorityByHash(hash)
        }
    }

    fun deleteTrustedAuthority(publicKey: PublicKey) {
        return this.deleteTrustedAuthority(publicKey.keyToHash().toHex())
    }

    fun contains(hash: String): Boolean {
        return this.trustedAuthorities.containsKey(hash)
    }


}
