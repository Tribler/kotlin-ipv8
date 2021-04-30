package nl.tudelft.ipv8.attestation.communication

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import mu.KotlinLogging
import nl.tudelft.ipv8.Peer
import nl.tudelft.ipv8.attestation.WalletAttestation
import nl.tudelft.ipv8.attestation.identity.IdentityCommunity
import nl.tudelft.ipv8.attestation.identity.Metadata
import nl.tudelft.ipv8.attestation.wallet.AttestationCommunity
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

const val DEFAULT_TIME_OUT = 30_000L
private val logger = KotlinLogging.logger {}

class CommunicationChannel(
    val attestationOverlay: AttestationCommunity,
    val identityOverlay: IdentityCommunity
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
    }

    val publicKeyBin
        get() = this.identityOverlay.myPeer.publicKey.keyToBin()

    val peers
        get() = this.identityOverlay.getPeers()

    val myPeer
        get() = this.identityOverlay.myPeer

    val schemas
        get() = this.attestationOverlay.schemaManager.getSchemaNames()

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
                this.identityOverlay.requestAttestationAdvertisement(
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

    private fun dropIdentityTableData() {
        TODO("")
    }

    private fun dropAttestationTableData() {
        TODO("")
    }

    fun remove() {
        TODO("")
    }

    fun getOfflineVerifiableAttributes(): List<AttestationPresentation> {
        val out = mutableListOf<AttestationPresentation>()
        val peer = this.myPeer
        val pseudonym = this.identityOverlay.identityManager.getPseudonym(peer.publicKey)

        for (credential in pseudonym.getCredentials()) {
            val attestations = credential.attestations.toList()

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
                    this.attestationOverlay.authorityManager.getTrustedAuthority(attestor.first)
                authority?.let {
                    it.publicKey?.verify(attestor.second, metadata.hash)
                } == true
            }) {
            logger.info("Not accepting ${attestationHash.toHex()}, no recognized authority or valid signature found.")
            return false
        }
        return true
    }

    fun generateChallenge(): Pair<Long, ByteArray> {
        val timestamp = System.currentTimeMillis()
        return Pair(timestamp, (this.myPeer.key as PrivateKey).sign(timestamp.toByteArray()))
    }
}

class AttestationPresentation(
    val attributeHash: ByteArray,
    val attributeName: String,
    val attributeValue: ByteArray,
    val idFormat: String,
    val signDate: Float,
    val metadata: Metadata,
    val attestors: List<Pair<ByteArray, ByteArray>>
) {
    override fun equals(other: Any?): Boolean {
        return other is AttestationPresentation && this.attributeHash.contentEquals(other.attributeHash) && this.attestors.size == other.attestors.size
    }
}

// class PrivateAttestationBlob(
//     val attributeName: String,
//     val attributeHash: ByteArray,
//     val metadataString: String,
//     val idFormat: String,
//     val attributeValue: String,
//     val signature: ByteArray,
//     val attestorKeys: List<String>
// ) {
//     override fun equals(other: Any?): Boolean {
//         return other is PrivateAttestationBlob && this.attributeName == other.attributeName && this.attributeHash.contentEquals(
//             other.attributeHash
//         ) && this.metadataString == other.metadataString && this.idFormat == other.idFormat && this.attributeValue == other.attributeValue && this.signature.contentEquals(
//             other.signature
//         ) && this.attestorKeys == other.attestorKeys
//     }
// }
