package nl.tudelft.ipv8.attestation.identity.manager

import mu.KotlinLogging
import nl.tudelft.ipv8.attestation.identity.datastructures.IdentityAttestation
import nl.tudelft.ipv8.attestation.identity.datastructures.Metadata
import nl.tudelft.ipv8.attestation.identity.store.Credential
import nl.tudelft.ipv8.attestation.identity.store.IdentityStore
import nl.tudelft.ipv8.attestation.identity.datastructures.tokenTree.Token
import nl.tudelft.ipv8.attestation.identity.datastructures.tokenTree.TokenTree
import nl.tudelft.ipv8.keyvault.PrivateKey
import nl.tudelft.ipv8.keyvault.PublicKey
import nl.tudelft.ipv8.messaging.serializeUInt
import nl.tudelft.ipv8.messaging.serializeUShort
import nl.tudelft.ipv8.util.ByteArrayKey
import nl.tudelft.ipv8.util.toHex
import nl.tudelft.ipv8.util.toKey
import org.json.JSONObject

private val logger = KotlinLogging.logger {}

class PseudonymManager(
    internal val database: IdentityStore,
    publicKey: PublicKey? = null,
    privateKey: PrivateKey? = null,
) {

    internal val tree = TokenTree(publicKey, privateKey)

    val publicKey: PublicKey
        get() = this.tree.publicKey

    private var credentials = this.database.getCredentialsFor(this.publicKey).toMutableList()

    init {
        logger.info("Loading public key ${this.publicKey.keyToHash().toHex()} from database")
        for (token in this.database.getTokensFor(this.publicKey)) {
            this.tree.elements[token.hash.toKey()] = token
        }
    }

    fun addCredential(
        token: Token,
        metadata: Metadata,
        attestations: Set<Pair<PublicKey, IdentityAttestation>> = setOf(),
    ): Credential? {
        if (this.tree.gatherToken(token) != null) {
            this.database.insertToken(this.publicKey, token)

            if (metadata.verify(this.publicKey) && metadata.tokenPointer.contentEquals(token.hash)) {
                this.database.insertMetadata(this.publicKey, metadata)

                val validAttestations = mutableSetOf<IdentityAttestation>()

                for ((authorityPublicKey, identityAttestation) in attestations) {
                    if (this.addAttestation(authorityPublicKey, identityAttestation)) {
                        validAttestations.add(identityAttestation)
                    }
                }

                val out = Credential(metadata, validAttestations)
                this.credentials.add(out)
                return out
            }
        }
        return null
    }

    fun addAttestation(publicKey: PublicKey, attestation: IdentityAttestation): Boolean {
        if (attestation.verify(publicKey)) {
            this.database.insertAttestation(this.publicKey, publicKey, attestation)
            return true
        }
        return false
    }

    fun addMetadata(metadata: Metadata): Boolean {
        if (metadata.verify(this.publicKey)) {
            this.database.insertMetadata(this.publicKey, metadata)
            return true
        }
        return false
    }

    fun createAttestation(metadata: Metadata, privateKey: PrivateKey): IdentityAttestation {
        return IdentityAttestation.create(metadata, privateKey)
    }

    fun createCredential(
        attestationHash: ByteArray,
        metadata: HashMap<String, Any>,
        after: Metadata? = null,
    ): Credential {
        val preceding = if (after == null) null else this.tree.elements[after.tokenPointer.toKey()]
        val token = this.tree.addByHash(attestationHash, preceding)
        val metadataObj = Metadata(
            token.hash,
            JSONObject(metadata).toString().toByteArray(),
            this.tree.privateKey
        )
        return this.addCredential(token, metadataObj, setOf())!!
    }

    fun getCredential(metadata: Metadata): Credential {
        return this.database.getCredentialOver(metadata)
    }

    fun getCredentials(): List<Credential> {
        return this.database.getCredentialsFor(this.publicKey)
    }

    fun discloseCredentials(
        credentials: List<Credential>,
        attestationSelector: Set<ByteArray>
    ): Disclosure {
        return createDisclosure(credentials.map { it.metadata }.toSet(), attestationSelector)
    }

    private fun createDisclosure(metadata: Set<Metadata>, attestationSelector: Set<ByteArray>): Disclosure {
        val attSelector = attestationSelector.map { ByteArrayKey(it) }
        var serializedMetadata = byteArrayOf()
        for (md in metadata) {
            val serialized = md.getPlaintextSigned()
            serializedMetadata += serializeUInt(serialized.size.toUInt()) + serialized
        }

        var attestations = byteArrayOf()
        var authorities = byteArrayOf()

        for (md in metadata) {
            val availableAttestations = this.database.getAttestationsOver(md)
            for (attestation in availableAttestations) {
                if (attSelector.contains(attestation.hash.toKey())) {
                    attestations += attestation.getPlaintextSigned()
                    val authority = this.database.getAuthority(attestation)
                    authorities += serializeUShort(authority.size) + authority
                }
            }
        }
        val requiredTokenHashes = metadata.map { it.tokenPointer }
        val tokens = mutableListOf<Token>()

        for (requiredTokenHash in requiredTokenHashes) {
            val rootToken = this.tree.elements[requiredTokenHash.toKey()]!!

            if (!this.tree.verify(rootToken)) {
                throw RuntimeException("Attempted to create disclosure for undisclosable Token!")
            }

            tokens.add(rootToken)
            var currentToken = rootToken

            while (!currentToken.previousTokenHash.contentEquals(this.tree.genesisHash)) {
                currentToken = this.tree.elements[currentToken.previousTokenHash.toKey()]!!
                tokens.add(currentToken)
            }
        }

        // This is the order that they are present in the tree, as we stepped backwards.
        tokens.reverse()
        var serializedTokens = byteArrayOf()
        tokens.forEach { serializedTokens += it.getPlaintextSigned() }
        return Disclosure(serializedMetadata, serializedTokens, attestations, authorities)
    }
}

class Disclosure(
    val metadata: ByteArray,
    val tokens: ByteArray,
    val attestations: ByteArray,
    val authorities: ByteArray,
) {
    operator fun component1(): ByteArray {
        return metadata
    }

    operator fun component2(): ByteArray {
        return tokens
    }

    operator fun component3(): ByteArray {
        return attestations
    }

    operator fun component4(): ByteArray {
        return authorities
    }
}

