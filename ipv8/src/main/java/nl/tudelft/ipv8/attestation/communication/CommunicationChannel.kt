package nl.tudelft.ipv8.attestation.communication

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import mu.KotlinLogging
import nl.tudelft.ipv8.Peer
import nl.tudelft.ipv8.attestation.identity.IdentityCommunity
import nl.tudelft.ipv8.attestation.identity.datastructures.Metadata
import nl.tudelft.ipv8.attestation.wallet.AttestationCommunity
import nl.tudelft.ipv8.attestation.communication.caches.DisclosureRequestCache
import nl.tudelft.ipv8.attestation.identity.datastructures.IdentityAttestation
import nl.tudelft.ipv8.attestation.identity.store.Credential
import nl.tudelft.ipv8.attestation.wallet.cryptography.WalletAttestation
import nl.tudelft.ipv8.attestation.revocation.RevocationCommunity
import nl.tudelft.ipv8.keyvault.PrivateKey
import nl.tudelft.ipv8.keyvault.PublicKey
import nl.tudelft.ipv8.keyvault.defaultCryptoProvider
import nl.tudelft.ipv8.util.ByteArrayKey
import nl.tudelft.ipv8.util.SettableDeferred
import nl.tudelft.ipv8.util.asMap
import nl.tudelft.ipv8.util.defaultEncodingUtils
import nl.tudelft.ipv8.util.sha3_256
import nl.tudelft.ipv8.util.stripSHA1Padding
import nl.tudelft.ipv8.util.toByteArray
import nl.tudelft.ipv8.util.toHex
import nl.tudelft.ipv8.util.toKey
import org.json.JSONObject
import java.util.UUID

const val DEFAULT_TIME_OUT = 30_000L
private val logger = KotlinLogging.logger {}

class CommunicationChannel(
    val attestationOverlay: AttestationCommunity,
    val identityOverlay: IdentityCommunity,
    val revocationOverlay: RevocationCommunity
) {
    val attestationRequests =
        hashMapOf<AttributePointer, Triple<SettableDeferred<ByteArray?>, String, String?>>()
    val verifyRequests = hashMapOf<AttributePointer, SettableDeferred<Boolean>>()
    val verificationOutput = hashMapOf<ByteArrayKey, List<Pair<ByteArray, Double?>>>()
    private val attestationMetadata = hashMapOf<AttributePointer, MutableMap<String, String>>()

    init {
        this.attestationOverlay.setAttestationRequestCallback(this::onRequestAttestationAsync)
        this.attestationOverlay.setAttestationRequestCompleteCallback(this::onAttestationComplete)
        this.attestationOverlay.setVerifyRequestCallback(this::onVerifyRequestAsync)
        this.identityOverlay.setAttestationPresentationCallback(this::onAttestationPresentationComplete)
    }

    val publicKeyBin
        get() = this.identityOverlay.myPeer.publicKey.keyToBin()

    val peers
        get() = this.identityOverlay.getPeers()

    val myPeer
        get() = this.identityOverlay.myPeer

    val schemas
        get() = this.attestationOverlay.schemaManager.getSchemaNames()

    val authorityManager
        get() = this.revocationOverlay.authorityManager

    private fun onRequestAttestationAsync(
        peer: Peer,
        attributeName: String,
        metadataString: String,
        proposedValue: String?
    ): Deferred<ByteArray?> {
        // Promise some ByteArray.
        val deferred = SettableDeferred<ByteArray?>()
        this.attestationRequests[AttributePointer(peer, attributeName)] =
            Triple(deferred, metadataString, proposedValue)
        @Suppress("UNCHECKED_CAST")
        this.attestationMetadata[AttributePointer(peer, attributeName)] =
            JSONObject(metadataString).asMap() as MutableMap<String, String>

        return GlobalScope.async(start = CoroutineStart.LAZY) { deferred.await() }
    }

    @Suppress("UNUSED_PARAMETER")
    private fun onAttestationComplete(
        forPeer: Peer,
        attributeName: String,
        attestation: WalletAttestation,
        attributeHash: ByteArray,
        idFormat: String,
        fromPeer: Peer?,
        value: ByteArray?
    ) {
        val metadata = this.attestationMetadata[AttributePointer(forPeer, attributeName)]!!
        value?.let { metadata["value"] = defaultEncodingUtils.encodeBase64ToString(sha3_256(it)) }

        if (forPeer == myPeer) {
            if (fromPeer == myPeer) {
                @Suppress("UNCHECKED_CAST")
                this.identityOverlay.selfAdvertise(
                    attributeHash, attributeName, idFormat,
                    metadata as HashMap<String, String>?
                )
            } else {
                @Suppress("UNCHECKED_CAST")
                this.identityOverlay.advertiseAttestation(
                    fromPeer!!, attributeHash, attributeName, idFormat,
                    metadata as HashMap<String, String>?
                )
            }
        } else {
            @Suppress("UNCHECKED_CAST")
            this.identityOverlay.addKnownHash(
                attributeHash, attributeName, value, forPeer.publicKey,
                metadata as HashMap<String, String>?
            )
        }
    }

    private fun onVerifyRequestAsync(peer: Peer, attributeHash: ByteArray): Deferred<Boolean?> {
        val metadata =
            this.identityOverlay.getAttestationByHash(attributeHash) ?: return CompletableDeferred(
                null
            )
        val attributeName = JSONObject(String(metadata.serializedMetadata)).getString("name")
        val deferred = SettableDeferred<Boolean>()
        this.verifyRequests[AttributePointer(peer, attributeName)] = deferred
        return GlobalScope.async(start = CoroutineStart.LAZY) { deferred.await() }
    }

    private fun onVerificationResults(attributeHash: ByteArray, value: List<Double>) {
        val references = this.verificationOutput[attributeHash.toKey()]
        val out = arrayListOf<Pair<ByteArray, Double>>()
        if (references != null) {
            for (i in references.indices) {
                out.add(Pair(references[i].first, value[i]))
            }
            this.verificationOutput[attributeHash.toKey()] = out
        } else {
            throw RuntimeException("Could not locate reference for ${attributeHash.toHex()} in verification output.")
        }
    }

    @Suppress("UNUSED_PARAMETER")
    private fun onAttestationPresentationComplete(
        peer: Peer,
        attributeHash: ByteArray,
        value: ByteArray,
        metadata: Metadata,
        attestations: List<IdentityAttestation>,
        disclosureInformation: String,
    ) {
        logger.info("Received correct attestation presentation with hash ${attributeHash.toHex()}.")
        val parsedMD = JSONObject(String(metadata.serializedMetadata))
        val idFormat = parsedMD.getString("schema")
        this.verify(peer, stripSHA1Padding(attributeHash), listOf(value), idFormat)
    }

    private fun dropIdentityTableData(): List<ByteArray> {
        val database = this.identityOverlay.identityManager.database
        return database.dropIdentityTable(this.myPeer.publicKey)
    }

    private fun dropAttestationTableData(attestationHashes: List<ByteArray>) {
        val database = this.attestationOverlay.database
        return database.deleteAttestations(attestationHashes)
    }

    fun deleteIdentity() {
        val hashes = this.dropIdentityTableData().map { stripSHA1Padding(it) }
        this.dropAttestationTableData(hashes)
        this.attestationRequests.clear()
        this.verifyRequests.clear()
    }

    fun requestAttestation(
        peer: Peer,
        attributeName: String,
        idFormat: String,
        metadata: HashMap<String, String>,
        proposedValue: String? = null,
    ) {
        val key = this.attestationOverlay.getIdAlgorithm(idFormat).generateSecretKey()
        this.attestationMetadata[AttributePointer(this.identityOverlay.myPeer, attributeName)] =
            metadata
        this.attestationOverlay.requestAttestation(
            peer,
            attributeName,
            key,
            metadata,
            proposedValue
        )
    }

    fun attest(peer: Peer, attributeName: String, value: ByteArray) {
        val outstanding = this.attestationRequests.remove(AttributePointer(peer, attributeName))!!
        GlobalScope.launch { outstanding.first.setResult(value) }
    }

    fun dismissAttestationRequest(peer: Peer, attributeName: String) {
        val outstanding = this.attestationRequests.remove(AttributePointer(peer, attributeName))!!
        GlobalScope.launch { outstanding.first.setResult(null) }
    }

    fun allowVerification(peer: Peer, attributeName: String) {
        val outstanding = this.verifyRequests.remove(AttributePointer(peer, attributeName))!!
        GlobalScope.launch { outstanding.setResult(true) }
    }

    fun disallowVerification(peer: Peer, attributeName: String) {
        val outstanding = this.verifyRequests.remove(AttributePointer(peer, attributeName))!!
        GlobalScope.launch { outstanding.setResult(false) }
    }

    fun generateAttestationPresentationRequest(
        attributeName: String
    ): String {
        val id = UUID.randomUUID().toString()
        this.identityOverlay.requestCache.add(
            DisclosureRequestCache(
                this.identityOverlay.requestCache,
                id,
                DisclosureRequest(attributeName),
            )
        )
        return id
    }

    fun presentAttestation(
        peer: Peer,
        requestId: String,
        attributeHash: ByteArray,
        attributeValue: ByteArray,
        credential: Credential,
    ) {
        val presentationMetadata = JSONObject()
        presentationMetadata.put("id", requestId)
        presentationMetadata.put("attestationHash", attributeHash.toHex())
        presentationMetadata.put("value", attributeValue.toHex())

        this.identityOverlay.presentAttestationAdvertisement(
            peer,
            credential,
            presentationMetadata.toString(),
        )
    }

    fun verify(
        peer: Peer,
        attestationHash: ByteArray,
        referenceValues: List<ByteArray>,
        idFormat: String
    ) {
        this.verificationOutput[attestationHash.toKey()] = referenceValues.map { Pair(it, null) }
        this.attestationOverlay.verifyAttestationValues(
            peer.address,
            attestationHash,
            referenceValues,
            this::onVerificationResults,
            idFormat
        )
    }

    fun revoke(signature: ByteArray) {
        return revoke(listOf(signature))
    }

    fun revoke(signatures: List<ByteArray>) {
        this.revocationOverlay.revokeAttestations(signatures)
    }

    fun verifyLocally(
        attestationHash: ByteArray,
        proposedAttestationValue: ByteArray,
        metadata: Metadata,
        subjectKey: PublicKey,
        challengePair: Pair<ByteArray, Long>,
        attestors: List<Pair<ByteArray, ByteArray>>
    ): Boolean {
        if (System.currentTimeMillis() - challengePair.second > DEFAULT_TIME_OUT) {
            logger.info("Not accepting ${attestationHash.toHex()}, challenge timed out!")
            return false
        }

        if (!subjectKey.verify(challengePair.first, challengePair.second.toByteArray())) {
            logger.info("Not accepting ${attestationHash.toHex()}, challenge not valid!")
            return false
        }

        if (!subjectKey.verify(metadata.signature, metadata.getPlaintext())) {
            logger.info("Not accepting ${attestationHash.toHex()}, metadata signature not valid!")
            return false
        }

        val parsedMD = JSONObject(String(metadata.serializedMetadata))
        val valueHash = parsedMD.getString("value")
        val proposedHash =
            defaultEncodingUtils.encodeBase64ToString(sha3_256(proposedAttestationValue))
        if (valueHash != proposedHash) {
            logger.info("Not accepting ${attestationHash.toHex()}, value not valid!")
            return false
        }

        // Check if authority is recognized and the corresponding signature is correct.
        if (!attestors.any { attestor ->
                val authority =
                    this.authorityManager.getTrustedAuthority(attestor.first)
                authority?.let {
                    it.publicKey?.verify(attestor.second, metadata.hash)
                } == true
            }) {
            logger.info("Not accepting ${attestationHash.toHex()}, no recognized authority or valid signature found.")
            return false
        }

        // Check if any authority has not revoked a signature
        if (!this.authorityManager.verify(metadata.hash)) {
            logger.info("Not accepting ${attestationHash.toHex()}, signature was revoked.")
            return false
        }

        return true
    }

    fun generateChallenge(): Pair<Long, ByteArray> {
        val timestamp = System.currentTimeMillis()
        return Pair(timestamp, (this.myPeer.key as PrivateKey).sign(timestamp.toByteArray()))
    }

    // Hash, Metadata, Value
    fun getAttributeByName(attributeName: String): Triple<ByteArray, Credential, ByteArray>? {
        val pseudonym = this.identityOverlay.identityManager.getPseudonym(myPeer.publicKey)

        for (credential in pseudonym.getCredentials()) {
            val attestations = credential.attestations.toList()
            val attestors = mutableListOf<ByteArray>()

            for (attestation in attestations) {
                attestors += this.identityOverlay.identityManager.database.getAuthority(attestation)
            }
            val attributeHash =
                pseudonym.tree.elements[credential.metadata.tokenPointer.toKey()]!!.contentHash
            val jsonMetadata = JSONObject(String(credential.metadata.serializedMetadata))
            val attributeValue =
                this.attestationOverlay.database.getValueByHash(
                    stripSHA1Padding(attributeHash)
                )!!

            if (jsonMetadata.getString("name").equals(attributeName, ignoreCase = true)) {
                return Triple(attributeHash, credential, attributeValue)
            }
        }
        return null
    }

    fun getOfflineVerifiableAttributes(publicKey: PublicKey = this.myPeer.publicKey): List<AttestationPresentation> {
        val out = mutableListOf<AttestationPresentation>()
        val pseudonym = this.identityOverlay.identityManager.getPseudonym(publicKey)

        for (credential in pseudonym.getCredentials()) {
            val attestations = credential.attestations.toList()
            if (attestations.isEmpty()) {
                continue
            }

            val attestors = mutableListOf<Pair<ByteArray, ByteArray>>()

            for (attestation in attestations) {
                val attestor = defaultCryptoProvider.keyFromPublicBin(
                    this.identityOverlay.identityManager.database.getAuthority(
                        attestation
                    )
                ).keyToHash()
                attestors += Pair(attestor, attestation.signature)
            }

            val attributeHash =
                pseudonym.tree.elements[credential.metadata.tokenPointer.toKey()]!!.contentHash
            val jsonMetadata = JSONObject(String(credential.metadata.serializedMetadata))

            val attributeName = jsonMetadata.getString("name")
            val attributeValue =
                this.attestationOverlay.database.getValueByHash(
                    stripSHA1Padding(attributeHash)
                )!!
            val idFormat = jsonMetadata.getString("schema")
            val signDate = jsonMetadata.getDouble("date").toFloat()
            out += AttestationPresentation(
                publicKey,
                attributeHash,
                attributeName,
                attributeValue,
                idFormat,
                signDate,
                credential.metadata,
                attestors
            )
        }
        return out.sortedBy { it.attributeName }
    }

    fun getAttributesSignedBy(peer: Peer): List<SubjectAttestationPresentation> {
        val db = this.identityOverlay.identityManager.database
        val myKeyHash = myPeer.publicKey.keyToHash()
        // Exclude own public key
        val subjects = db.getKnownSubjects() - peer.publicKey
        val out = mutableListOf<SubjectAttestationPresentation>()
        for (subject in subjects) {
            val mdList = db.getMetadataFor(subject)
            for (metadata in mdList) {
                val md = JSONObject(String(metadata.serializedMetadata))
                out.add(
                    SubjectAttestationPresentation(
                        subject,
                        metadata.hash,
                        metadata,
                        md.getString("name"),
                        md.getDouble("date").toFloat(),
                        !this.revocationOverlay.authorityManager.verify(metadata.hash, myKeyHash)
                    )
                )
            }
        }
        return out.sortedBy { -it.signDate }
    }

    @Deprecated("This should not be used.")
    fun getAttributes(peer: Peer): HashMap<ByteArrayKey, Triple<String, HashMap<String, Any?>, List<ByteArray>>> {
        val pseudonym = this.identityOverlay.identityManager.getPseudonym(peer.publicKey)
        val out: HashMap<ByteArrayKey, Triple<String, HashMap<String, Any?>, List<ByteArray>>> =
            hashMapOf()

        for (credential in pseudonym.getCredentials()) {
            val attestations = credential.attestations.toList()
            val attestors = mutableListOf<ByteArray>()

            for (attestation in attestations) {
                attestors += this.identityOverlay.identityManager.database.getAuthority(attestation)
            }
            val attributeHash =
                pseudonym.tree.elements[credential.metadata.tokenPointer.toKey()]!!.contentHash
            val jsonMetadata = JSONObject(String(credential.metadata.serializedMetadata))
            out[attributeHash.toKey()] =
                Triple(
                    jsonMetadata.getString("name"),
                    jsonMetadata.asMap() as HashMap<String, Any?>,
                    attestors
                )
        }
        return out
    }
}

class SubjectAttestationPresentation(
    val publicKey: PublicKey,
    val metadataHash: ByteArray,
    val metadata: Metadata,
    val attributeName: String,
    val signDate: Float,
    val isRevoked: Boolean
) {
    override fun equals(other: Any?): Boolean {
        return other is SubjectAttestationPresentation && this.publicKey == other.publicKey && this.metadata == other.metadata && this.isRevoked == other.isRevoked
    }

    override fun hashCode(): Int {
        var result = publicKey.hashCode()
        result = 31 * result + metadataHash.contentHashCode()
        result = 31 * result + metadata.hashCode()
        result = 31 * result + attributeName.hashCode()
        result = 31 * result + signDate.hashCode()
        return result
    }
}

class AttestationPresentation(
    val publicKey: PublicKey,
    val attributeHash: ByteArray,
    val attributeName: String,
    val attributeValue: ByteArray,
    val idFormat: String,
    val signDate: Float,
    val metadata: Metadata,
    val attestors: List<Pair<ByteArray, ByteArray>>
) {
    override fun equals(other: Any?): Boolean {
        return other is AttestationPresentation && this.publicKey == other.publicKey && this.attributeHash.contentEquals(
            other.attributeHash
        ) && this.attestors.size == other.attestors.size
    }

    override fun hashCode(): Int {
        var result = publicKey.hashCode()
        result = 31 * result + attributeHash.contentHashCode()
        result = 31 * result + attributeName.hashCode()
        result = 31 * result + attributeValue.contentHashCode()
        result = 31 * result + idFormat.hashCode()
        result = 31 * result + signDate.hashCode()
        result = 31 * result + metadata.hashCode()
        result = 31 * result + attestors.hashCode()
        return result
    }
}

class DisclosureRequest(
    val attributeName: String
)
