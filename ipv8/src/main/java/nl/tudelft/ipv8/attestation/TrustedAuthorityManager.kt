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


    fun loadDefaultAuthorities() {
        TODO("Preinstalled Authorities yet to be designed.")
    }

    fun addTrustedAuthority(publicKey: PublicKey) {
        val hash = publicKey.keyToHash().toHex()
        database.insertAuthority(publicKey, hash)
        synchronized(lock) {
            this.trustedAuthorities[hash] = Authority(publicKey, hash)
        }
    }

    fun contains(hash: String): Boolean {
        return this.trustedAuthorities.containsKey(hash)
    }

}
