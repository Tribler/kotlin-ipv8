package nl.tudelft.ipv8.attestation.identity.manager

import nl.tudelft.ipv8.attestation.identity.datastructures.IdentityAttestation
import nl.tudelft.ipv8.attestation.identity.datastructures.Metadata
import nl.tudelft.ipv8.attestation.identity.store.IdentityStore
import nl.tudelft.ipv8.keyvault.Key
import nl.tudelft.ipv8.keyvault.PrivateKey
import nl.tudelft.ipv8.keyvault.PublicKey
import nl.tudelft.ipv8.keyvault.defaultCryptoProvider
import nl.tudelft.ipv8.messaging.SERIALIZED_UINT_SIZE
import nl.tudelft.ipv8.messaging.SERIALIZED_USHORT_SIZE
import nl.tudelft.ipv8.messaging.deserializeUInt
import nl.tudelft.ipv8.messaging.deserializeUShort
import nl.tudelft.ipv8.util.ByteArrayKey
import nl.tudelft.ipv8.util.toKey

class IdentityManager(internal var database: IdentityStore) {

    val pseudonyms = hashMapOf<ByteArrayKey, PseudonymManager>()

    fun getPseudonym(key: Key): PseudonymManager {
        val publicKeyMaterial = key.pub().keyToBin()
        if (!this.pseudonyms.containsKey(publicKeyMaterial.toKey())) {
            if (key is PrivateKey) {
                this.pseudonyms[publicKeyMaterial.toKey()] = PseudonymManager(this.database, privateKey = key)
            } else {
                this.pseudonyms[publicKeyMaterial.toKey()] =
                    PseudonymManager(this.database, publicKey = key as PublicKey)
            }
        }
        return this.pseudonyms[publicKeyMaterial.toKey()]!!
    }

    fun substantiate(
        publicKey: PublicKey,
        serializeMetadata: ByteArray,
        serializedTokens: ByteArray,
        serializedAttestations: ByteArray,
        serializedAuthorities: ByteArray,
    ): Pair<Boolean, PseudonymManager> {
        val pseudonym = this.getPseudonym(publicKey)
        var correct = pseudonym.tree.deserializePublic(serializedTokens)

        var metadataOffset = 0
        while (metadataOffset < serializeMetadata.size) {
            val metadataSize = deserializeUInt(serializeMetadata, metadataOffset).toInt()
            metadataOffset += SERIALIZED_UINT_SIZE
            val metadata =
                Metadata.deserialize(
                    serializeMetadata.copyOfRange(metadataOffset, metadataOffset + metadataSize),
                    publicKey
                )
            pseudonym.addMetadata(metadata)
            metadataOffset += metadataSize
        }

        var attestationOffset = 0
        var authorityOffset = 0

        while (authorityOffset < serializedAuthorities.size) {
            val authoritySize = deserializeUShort(serializedAuthorities, authorityOffset)
            authorityOffset += SERIALIZED_USHORT_SIZE
            val authority = defaultCryptoProvider.keyFromPublicBin(
                serializedAuthorities.copyOfRange(
                    authorityOffset,
                    authorityOffset + authoritySize
                )
            )
            authorityOffset += authoritySize
            correct = correct and pseudonym.addAttestation(
                authority,
                IdentityAttestation.deserialize(serializedAttestations, authority, attestationOffset)
            )
            attestationOffset += 32 + authority.getSignatureLength()
        }

        return Pair(correct, pseudonym)
    }
}
