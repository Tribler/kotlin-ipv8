package nl.tudelft.ipv8.attestation

import kotlinx.coroutines.*
import mu.KotlinLogging
import nl.tudelft.ipv8.IPv8
import nl.tudelft.ipv8.Peer
import nl.tudelft.ipv8.attestation.identity.IdentityCommunity
import nl.tudelft.ipv8.attestation.identity.createCommunity
import nl.tudelft.ipv8.attestation.identity.database.IdentityStore
import nl.tudelft.ipv8.attestation.identity.manager.IdentityManager
import nl.tudelft.ipv8.attestation.wallet.AttestationCommunity
import nl.tudelft.ipv8.attestation.wallet.AttestationStore
import nl.tudelft.ipv8.keyvault.PrivateKey
import nl.tudelft.ipv8.keyvault.defaultCryptoProvider
import nl.tudelft.ipv8.util.ByteArrayKey
import nl.tudelft.ipv8.util.defaultEncodingUtils
import nl.tudelft.ipv8.util.toHex
import nl.tudelft.ipv8.util.toKey
import org.json.JSONObject
import java.io.File
import java.io.FileNotFoundException
import java.nio.file.Files
import java.nio.file.Paths

private val logger = KotlinLogging.logger {}

class CommunicationManager(
    private val iPv8Instance: IPv8,
    private val attestationStore: AttestationStore,
    private val identityStore: IdentityStore,
    private val authorityManager: AuthorityManager,
    private val storePseudonym: ((String, PrivateKey) -> Unit)? = null,
    private val loadPseudonym: ((String) -> PrivateKey)? = null,
) {
    private val channels = hashMapOf<ByteArrayKey, CommunicationChannel>()
    private val nameToChannel = hashMapOf<String, CommunicationChannel>()
    private val loadedCommunity
        get() = iPv8Instance.getOverlay<IdentityCommunity>()
    var identityManager = loadedCommunity?.identityManager


    fun lazyIdentityManager(): IdentityManager {
        if (this.identityManager == null) {
            this.identityManager = IdentityManager(identityStore)
        }
        return this.identityManager!!
    }

    fun load(
        name: String,
        rendezvousToken: String? = null,
    ): CommunicationChannel {
        if (nameToChannel.containsKey(name)) {
            return this.nameToChannel[name]!!
        }

        // Load private key with function or via default method.
        var privateKey = loadPseudonym?.let { it(name) } ?: loadPseudonym(name)
        if (privateKey == null) {
            privateKey = defaultCryptoProvider.generateKey()
            storePseudonym?.let { it(name, privateKey) } ?: storePseudonym(name, privateKey)
        }
        val publicKeyBytes = privateKey.pub().keyToBin()

        if (!this.channels.containsKey(publicKeyBytes.toKey())) {
            val decodedRendezvousToken: ByteArray? =
                (rendezvousToken?.let { defaultEncodingUtils.decodeBase64FromString(it) })

            val identityOverlay = createCommunity(
                privateKey,
                this.iPv8Instance,
                this.lazyIdentityManager(),
                identityStore,
                decodedRendezvousToken
            )

            val attestationOverlay = AttestationCommunity(
                identityOverlay.myPeer,
                identityOverlay.endpoint,
                identityOverlay.network,
                authorityManager,
                attestationStore
            )
            attestationOverlay.load()

            this.channels[publicKeyBytes.toKey()] = CommunicationChannel(attestationOverlay, identityOverlay)
            this.nameToChannel[name] = this.channels[publicKeyBytes.toKey()]!!
        }
        return this.nameToChannel[name]!!
    }

    fun unload(name: String) {
        TODO()
    }

    fun listPseudonyms(): List<String> {
        return getPseudonyms()
    }

    fun listLoadedPseudonyms(): List<String> {
        val pseudonyms = this.listPseudonyms()
        return this.nameToChannel.keys.filter { pseudonyms.contains(it) }
    }

    fun shutdown() {
        TODO()
    }

    companion object {
        private const val PSEUDONYM_PATH = "\\pseudonyms\\"

        // Note. This is not secure and should not be used in a production setting without encryption.
        @Suppress("NewApi")
        fun loadPseudonym(name: String): PrivateKey? {
            return try {
                val directoryPath = "${System.getProperty("user.dir")}\\$PSEUDONYM_PATH"
                val directory = File(directoryPath)
                if (!directory.exists()) {
                    directory.mkdirs()
                }
                val bytes = File("$directoryPath\\$name").readBytes()
                if (bytes.isNotEmpty()) {
                    defaultCryptoProvider.keyFromPrivateBin(bytes)
                } else {
                    null
                }
            } catch (e: FileNotFoundException) {
                logger.error("Failed to locate pseudonym file $name")
                null
            }
        }

        // Note. This is not secure and should not be used in a production setting without encryption.
        @Suppress("NewApi")
        fun storePseudonym(name: String, privateKey: PrivateKey) {
            val directoryPath = "${System.getProperty("user.dir")}\\$PSEUDONYM_PATH"
            val directory = File(directoryPath)
            if (!directory.exists()) {
                directory.mkdirs()
            }
            File("$directoryPath\\$name").writeBytes(privateKey.keyToBin())
        }

        @Suppress("NewApi", "UNCHECKED_CAST")
        fun getPseudonyms(): List<String> {
            return Files.walk(Paths.get("${System.getProperty("user.dir")}\\$PSEUDONYM_PATH"))
                .filter(Files::isRegularFile).map { it.fileName.toString() }
                .toArray().toList() as List<String>
        }
    }

}

class AttributePointer(val peer: Peer, val attributeName: String) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as AttributePointer

        if (peer != other.peer) return false
        if (attributeName != other.attributeName) return false

        return true
    }

    override fun hashCode(): Int {
        var result = peer.hashCode()
        result = 31 * result + attributeName.hashCode()
        return result
    }
}

class CommunicationChannel(val attestationOverlay: AttestationCommunity, val identityOverlay: IdentityCommunity) {

    private val attestationRequests = hashMapOf<AttributePointer, Pair<Deferred<ByteArray?>, String>>()
    private val verifyRequests = hashMapOf<AttributePointer, Deferred<Boolean?>>()
    private val verificationOutput = hashMapOf<ByteArrayKey, List<Pair<ByteArray, Double?>>>()
    private val attestationMetadata = hashMapOf<AttributePointer, Map<String, String>>()

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
    ): Deferred<ByteArray?> {
        // Promise some ByteArray.
        val deferred: Deferred<ByteArray?> = GlobalScope.async(start = CoroutineStart.LAZY) { null }
        this.attestationRequests[AttributePointer(peer, attributeName)] = Pair(deferred, metadataString)
        this.attestationMetadata[AttributePointer(peer, attributeName)]
        return deferred
    }

    @Suppress("UNUSED_PARAMETER")
    private fun onAttestationComplete(
        forPeer: Peer,
        attributeName: String,
        attestation: WalletAttestation,
        attributeHash: ByteArray,
        idFormat: String,
        fromPeer: Peer?,
        metaData: String?,
        Signature: ByteArray?,
    ) {
        val metadata = this.attestationMetadata.get(AttributePointer(forPeer, attributeName))

        if (forPeer == myPeer) {
            if (fromPeer == myPeer) {
                @Suppress("UNCHECKED_CAST")
                this.identityOverlay.selfAdvertise(attributeHash, attributeName, idFormat,
                    JSONObject(metadata).toMap() as HashMap<String, String>?)
            } else {
                this.identityOverlay.requestAttestationAdvertisement(fromPeer!!, attributeHash, attributeName, idFormat,
                    metadata as HashMap<String, String>?)
            }
        } else {
            this.identityOverlay.addKnownHash(attributeHash, attributeName, forPeer.publicKey,
                metadata as HashMap<String, String>?)
        }
    }

    private fun onVerifyRequestAsync(peer: Peer, attributeHash: ByteArray): Deferred<Boolean> {
        val metadata = this.identityOverlay.getAttestationByHash(attributeHash) ?: return CompletableDeferred(null)
        val attributeName = JSONObject(String(metadata.serializedMetadata)).getString("name")
        val deferred = GlobalScope.async(start = CoroutineStart.LAZY) { false }
        this.verifyRequests[AttributePointer(peer, attributeName)] = deferred
        return deferred
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

    fun getAttributes(peer: Peer): HashMap<ByteArrayKey, Triple<String, HashMap<String, Any>, List<ByteArray>>> {
        val pseudonym = this.identityOverlay.identityManager.getPseudonym(peer.publicKey)
        val out: HashMap<ByteArrayKey, Triple<String, HashMap<String, Any>, List<ByteArray>>> = hashMapOf()

        for (credential in pseudonym.getCredentials()) {
            val attestations = credential.attestations.toList()
            val attesters = listOf<ByteArray>()

            for (attestation in attestations) {
                attesters.plus(this.identityOverlay.identityManager.database.getAuthority(attestation))
            }
            val attributeHash = pseudonym.tree.elements[credential.metadata.tokenPointer.toKey()]!!.contentHash
            val jsonMetadata = JSONObject(String(credential.metadata.serializedMetadata))
            out[attributeHash.toKey()] =
                Triple(jsonMetadata.getString("name"), jsonMetadata.toMap() as HashMap<String, Any>, attesters)
        }
        return out
    }

    fun requestAttestation(peer: Peer, attributeName: String, idFormat: String, metadata: HashMap<String, String>) {
        val key = this.attestationOverlay.getIdAlgorithm(idFormat).generateSecretKey()
        metadata.put("id_format", idFormat)
        this.attestationMetadata[AttributePointer(peer, attributeName)] = metadata
        this.attestationOverlay.requestAttestation(peer, attributeName, key, metadata)
    }

    fun attest(peer: Peer, attributeName: String, value: ByteArray) {
        val outstanding = this.attestationRequests.remove(AttributePointer(peer, attributeName))!!
        GlobalScope.launch { outstanding.first.await() }
    }

    fun allowVerification(peer: Peer, attributeName: String) {
        val outstanding = this.verifyRequests.remove(AttributePointer(peer, attributeName))!!
        GlobalScope.launch { outstanding.await() }
    }

    fun disallowVerification(peer: Peer, attributeName: String) {
        val outstanding = this.verifyRequests.remove(AttributePointer(peer, attributeName))!!
        GlobalScope.launch { outstanding.await() }
    }

    fun verify(peer: Peer, attributeHash: ByteArray, referenceValues: List<ByteArray>, idFormat: String) {
        this.verificationOutput[attributeHash.toKey()] = referenceValues.map { Pair(it, null) }
        this.attestationOverlay.verifyAttestationValues(peer.address,
            attributeHash,
            referenceValues,
            this::onVerificationResults,
            idFormat)
    }

}
