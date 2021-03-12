package nl.tudelft.ipv8.attestation.identity.database

import nl.tudelft.ipv8.attestation.identity.IdentityAttestation
import nl.tudelft.ipv8.attestation.identity.Metadata
import nl.tudelft.ipv8.attestation.tokenTree.Token
import nl.tudelft.ipv8.keyvault.PublicKey

class Credential(val metadata: Metadata, val attestations: Set<IdentityAttestation>)

interface IdentityAttestationStore {

    fun insertToken(publicKey: PublicKey, token: Token)
    fun insertMetadata(publicKey: PublicKey, metadata: Metadata)
    fun insertAttestation(publicKey: PublicKey, authorityKey: PublicKey, attestation: IdentityAttestation)
    fun getTokensFor(publicKey: PublicKey): List<Token>
    fun getMetadataFor(publicKey: PublicKey): List<Metadata>
    fun getAttestationsFor(publicKey: PublicKey): Set<IdentityAttestation>
    fun getAttestationsBy(publicKey: PublicKey): Set<IdentityAttestation>
    fun getAttestationsOver(metadata: Metadata): Set<IdentityAttestation>
    fun getAuthority(attestation: IdentityAttestation): ByteArray
    fun getCredentialOver(metadata: Metadata): Credential
    fun getCredentialsFor(publicKey: PublicKey): List<Credential>
    fun getKnownIdentities(): List<PublicKey>

}
