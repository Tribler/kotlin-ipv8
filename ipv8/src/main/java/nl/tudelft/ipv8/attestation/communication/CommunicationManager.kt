package nl.tudelft.ipv8.attestation.communication

import mu.KotlinLogging
import nl.tudelft.ipv8.IPv8
import nl.tudelft.ipv8.Peer
import nl.tudelft.ipv8.attestation.common.AuthorityManager
import nl.tudelft.ipv8.attestation.identity.IdentityCommunity
import nl.tudelft.ipv8.attestation.identity.createCommunity
import nl.tudelft.ipv8.attestation.identity.datastructures.IdentityAttestation
import nl.tudelft.ipv8.attestation.identity.store.IdentityStore
import nl.tudelft.ipv8.attestation.identity.manager.IdentityManager
import nl.tudelft.ipv8.attestation.revocation.RevocationCommunity
import nl.tudelft.ipv8.attestation.wallet.AttestationCommunity
import nl.tudelft.ipv8.attestation.wallet.store.AttestationStore
import nl.tudelft.ipv8.keyvault.PrivateKey
import nl.tudelft.ipv8.keyvault.defaultCryptoProvider
import nl.tudelft.ipv8.util.*
import java.io.File
import java.io.FileNotFoundException
import java.nio.file.Files
import java.nio.file.Paths

private val logger = KotlinLogging.logger {}

class CommunicationManager(
    private val iPv8Instance: IPv8,
    private val attestationStore: AttestationStore,
    private val identityStore: IdentityStore,
    val authorityManager: AuthorityManager,
    storePseudonym: ((String, PrivateKey) -> Unit)? = null,
    loadPseudonym: ((String) -> PrivateKey?)? = null,
) {
    private val channels = hashMapOf<ByteArrayKey, CommunicationChannel>()
    private val nameToChannel = hashMapOf<String, CommunicationChannel>()
    private val loadedCommunity
        get() = iPv8Instance.getOverlay<IdentityCommunity>()
    var identityManager = loadedCommunity?.identityManager

    private val loadPseudonym = loadPseudonym ?: Companion::loadPseudonym
    private val storePseudonym = storePseudonym ?: Companion::storePseudonym

    private lateinit var attestationCallback: (peer: Peer, attestation: IdentityAttestation) -> Unit

    fun setAttestationCallback(f: (peer: Peer, attestation: IdentityAttestation) -> Unit) {
        this.attestationCallback = f
        channels.values.forEach { it.identityOverlay.setAttestationCallback(f) }
    }

    private fun lazyIdentityManager(): IdentityManager {
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
        var privateKey = this.loadPseudonym(name)
        if (privateKey == null) {
            privateKey = defaultCryptoProvider.generateKey()
            this.storePseudonym(name, privateKey)
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
            if (this::attestationCallback.isInitialized) {
                identityOverlay.setAttestationCallback(this.attestationCallback)
            }

            val attestationOverlay = AttestationCommunity(
                identityOverlay.myPeer,
                identityOverlay.endpoint,
                identityOverlay.network,
                attestationStore
            )
            attestationOverlay.load()

            val revocationOverlay = RevocationCommunity(
                identityOverlay.myPeer,
                identityOverlay.endpoint,
                identityOverlay.network,
                authorityManager,
                identityOverlay::getPeers
            )
            revocationOverlay.load()

            this.channels[publicKeyBytes.toKey()] =
                CommunicationChannel(attestationOverlay, identityOverlay, revocationOverlay)
            this.nameToChannel[name] = this.channels[publicKeyBytes.toKey()]!!
        }
        return this.nameToChannel[name]!!
    }

    fun unload(name: String) {
        if (this.nameToChannel.containsKey(name)) {
            val communicationChannel = this.nameToChannel.remove(name)!!
            this.channels.remove(communicationChannel.publicKeyBin.toKey())
            this.iPv8Instance.unloadSecondaryOverlayStrategy(communicationChannel.identityOverlay.serviceId)
            communicationChannel.attestationOverlay.unload()
            communicationChannel.revocationOverlay.unload()
            // TODO: Endpoint close?
        }
    }

    fun listPseudonyms(): List<String> {
        return getPseudonyms()
    }

    fun listLoadedPseudonyms(): List<String> {
        val pseudonyms = this.listPseudonyms()
        return this.nameToChannel.keys.filter { pseudonyms.contains(it) }
    }

    fun shutdown() {
        for (name in this.nameToChannel.keys) {
            this.unload(name)
        }
    }

    fun getAllPeers(): List<Peer> {
        val peers = mutableListOf<Peer>()
        this.channels.values.map { peers += it.peers }
        return peers
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
