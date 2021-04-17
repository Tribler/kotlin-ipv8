package nl.tudelft.ipv8.attestation.identity

import mu.KotlinLogging
import nl.tudelft.ipv8.*
import nl.tudelft.ipv8.attestation.identity.database.Credential
import nl.tudelft.ipv8.attestation.identity.database.IdentityStore
import nl.tudelft.ipv8.attestation.identity.manager.Disclosure
import nl.tudelft.ipv8.attestation.identity.manager.IdentityManager
import nl.tudelft.ipv8.attestation.identity.manager.PseudonymManager
import nl.tudelft.ipv8.attestation.identity.payloads.AttestPayload
import nl.tudelft.ipv8.attestation.identity.payloads.DisclosePayload
import nl.tudelft.ipv8.attestation.identity.payloads.MissingResponsePayload
import nl.tudelft.ipv8.attestation.identity.payloads.RequestMissingPayload
import nl.tudelft.ipv8.attestation.schema.ID_METADATA
import nl.tudelft.ipv8.attestation.tokenTree.Token
import nl.tudelft.ipv8.keyvault.PrivateKey
import nl.tudelft.ipv8.keyvault.PublicKey
import nl.tudelft.ipv8.messaging.Packet
import nl.tudelft.ipv8.peerdiscovery.Network
import nl.tudelft.ipv8.peerdiscovery.strategy.RandomWalk
import nl.tudelft.ipv8.util.ByteArrayKey
import nl.tudelft.ipv8.util.padSHA1Hash
import nl.tudelft.ipv8.util.toHex
import nl.tudelft.ipv8.util.toKey
import org.json.JSONObject
import kotlin.math.max

const val SAFE_UDP_PACKET_LENGTH = 1296
const val DEFAULT_TIME_OUT = 300
const val TOKEN_SIZE = 64

const val DISCLOSURE_PAYLOAD = 1
const val ATTEST_PAYLOAD = 2
const val REQUEST_MISSING_PAYLOAD = 3
const val MISSING_RESPONSE_PAYLOAD = 4

// TODO: "clean up".
val DEFAULT_METADATA = arrayOf("name", "date", "schema", "signature", "public_key", "attribute")

class HashInformation(
    val name: String,
    val time: Float,
    val publicKey: PublicKey,
    val metadata: HashMap<String, String>?,
)

private val logger = KotlinLogging.logger {}

class IdentityCommunity(
    override var myPeer: Peer,
    identityManager: IdentityManager? = null,
    database: IdentityStore,
    override var serviceId: String = SERVICE_ID,
    override var network: Network = Network(),
) : Community() {

    var identityManager: IdentityManager = identityManager ?: IdentityManager(database)
    private val knownAttestationHashes = hashMapOf<ByteArrayKey, HashInformation>()
    private val pseudonymManager = this.identityManager.getPseudonym(this.myPeer.key)

    private var tokenChain: List<Token> = mutableListOf()
    private var metadataChain: List<Metadata> = mutableListOf()
    private var attestationChain: List<IdentityAttestation> = mutableListOf()
    private val permissions: HashMap<Peer, Int> = hashMapOf()

    init {
        super.myPeer = myPeer
        super.network = network

        for (token in this.pseudonymManager.tree.elements.values) {
            val chain = this.pseudonymManager.tree.getRootPath(token)
            if (chain.size > this.tokenChain.size) {
                this.tokenChain = chain
            }
        }
        for (credential in this.pseudonymManager.getCredentials()) {
            for (token in this.tokenChain) {
                if (credential.metadata.tokenPointer.contentEquals(token.hash)) {
                    this.metadataChain += credential.metadata
                    this.attestationChain += credential.attestations
                    break
                }
            }
        }

        messageHandlers[DISCLOSURE_PAYLOAD] = ::onDisclosureWrapper
        messageHandlers[ATTEST_PAYLOAD] = ::onAttestWrapper
        messageHandlers[REQUEST_MISSING_PAYLOAD] = ::onRequestMissingWrapper
        messageHandlers[MISSING_RESPONSE_PAYLOAD] = ::onMissingResponseWrapper
    }

    private fun onDisclosureWrapper(packet: Packet) {
        val (peer, payload) = packet.getAuthPayload(DisclosePayload.Deserializer)
        logger.info("Received Disclose payload from ${peer.mid}.")
        this.onDisclosure(peer, payload)
    }

    private fun onAttestWrapper(packet: Packet) {
        val (peer, payload) = packet.getAuthPayload(AttestPayload.Deserializer)
        logger.info("Received Attest payload from ${peer.mid}.")
        this.onAttest(peer, payload)
    }

    private fun onRequestMissingWrapper(packet: Packet) {
        val (peer, payload) = packet.getAuthPayload(RequestMissingPayload.Deserializer)
        logger.info("Received Request Missing payload from ${peer.mid}.")
        this.onRequestMissing(peer, payload)
    }

    private fun onMissingResponseWrapper(packet: Packet) {
        val (peer, payload) = packet.getAuthPayload(MissingResponsePayload.Deserializer)
        logger.info("Received Missing Response payload from ${peer.mid}.")
        this.onMissingResponse(peer, payload)
    }

    fun addKnownHash(
        attributeHash: ByteArray,
        name: String,
        publicKey: PublicKey,
        metadata: HashMap<String, String>? = null,
    ) {
        val hash = if (attributeHash.size == 20) padSHA1Hash(attributeHash) else attributeHash
        this.knownAttestationHashes[ByteArrayKey(hash)] =
            HashInformation(name, System.currentTimeMillis() / 1000F, publicKey, metadata)
    }

    fun getAttestationByHash(attributeHash: ByteArray): Metadata? {
        val hash = if (attributeHash.size == 20) padSHA1Hash(attributeHash) else attributeHash
        for (credential in this.pseudonymManager.getCredentials()) {
            val token = this.pseudonymManager.tree.elements.get(ByteArrayKey(credential.metadata.tokenPointer))
            if (token?.hash.contentEquals(hash)) {
                return credential.metadata
            }
        }
        return null
    }

    fun shouldSign(pseudonym: PseudonymManager, metadata: Metadata): Boolean {
        val transaction = JSONObject(String(metadata.serializedMetadata))
        val requestedKeys = transaction.keySet()
        if (!pseudonym.tree.elements.containsKey(metadata.tokenPointer.toKey())) {
            return false
        }
        val attributeHash = pseudonym.tree.elements[metadata.tokenPointer.toKey()]!!.contentHash
        if ("name" !in requestedKeys || "date" !in requestedKeys || "schema" !in requestedKeys) {
            logger.debug("Not signing $metadata, it doesn't include the required fields!")
            return false
        }
        if (!this.knownAttestationHashes.containsKey(attributeHash.toKey())) {
            logger.debug("Not signing $metadata, it doesn't point to known content!")
            return false
        }
        if (!pseudonym.publicKey.keyToBin()
                .contentEquals(this.knownAttestationHashes[attributeHash.toKey()]?.publicKey?.keyToBin())
        ) {
            logger.debug("Not signing $metadata, attribute doesn't belong to key!")
            return false
        }
        // Refuse to sign blocks older than 5 minutes
        if (System.currentTimeMillis() / 1000F > this.knownAttestationHashes[attributeHash.toKey()]?.time?.plus((DEFAULT_TIME_OUT)) ?: 0F) {
            logger.debug("Not signing $metadata, timed out!")
            return false
        }
        if (transaction["name"] != this.knownAttestationHashes[attributeHash.toKey()]?.name) {
            logger.debug("Not signing $metadata, name does not match!")
            return false
        }
        if (this.knownAttestationHashes[attributeHash.toKey()]!!.metadata != null
            && transaction.toMap().filterKeys { it !in DEFAULT_METADATA }
            // TODO: Remove filter here.
            != this.knownAttestationHashes[attributeHash.toKey()]!!.metadata!!.filterKeys { it !in DEFAULT_METADATA }
        ) {
            logger.debug("Not signing $metadata, metadata does not match!")
            return false
        }
        for (attestation in pseudonym.database.getAttestationsOver(metadata)) {
            if (this.myPeer.publicKey.keyToBin().contentEquals(pseudonym.database.getAuthority(attestation))) {
                logger.debug("Not signing $metadata, already attested!")
                return false
            }
        }
        return true
    }

    private fun fitDisclosure(disclosure: Disclosure): Disclosure {
        val tokenSize = 64 + this.myPeer.publicKey.getSignatureLength()
        val (metadata, tokens, attestations, authorities) = disclosure
        var tokensOut = tokens
        val metaLength = metadata.size + attestations.size + authorities.size
        if (metaLength + tokens.size > SAFE_UDP_PACKET_LENGTH) {
            var packetSpace = SAFE_UDP_PACKET_LENGTH - metaLength
            if (packetSpace < 0) {
                logger.warn("Attempting to disclose with packet of length $metaLength, hoping for the best!")
            }
            packetSpace = max(0, packetSpace)
            val trimLength = packetSpace / tokenSize
            tokensOut = tokens.copyOfRange(tokens.size - (trimLength * tokenSize), tokens.size)
        }
        return Disclosure(metadata, tokensOut, attestations, authorities)
    }

    private fun receivedDisclosureForAttest(peer: Peer, disclosure: Disclosure) {
        val solicited = this.knownAttestationHashes.values.filter { it.publicKey == peer.publicKey }
        if (solicited.isNotEmpty()) {
            val (correct, pseudonym) = this.identityManager.substantiate(
                peer.publicKey,
                disclosure.metadata,
                disclosure.tokens,
                disclosure.attestations,
                disclosure.authorities
            )
            val requiredAttributes =
                this.knownAttestationHashes.filter { it.value.publicKey == peer.publicKey }.keys.toTypedArray()
            val knownAttributes: List<ByteArrayKey> =
                pseudonym.tree.elements.values.map { ByteArrayKey(it.contentHash) }

            if (correct && requiredAttributes.any { knownAttributes.contains(it) }) {
                for (credential in pseudonym.getCredentials()) {
                    logger.info("Attesting to ${credential.metadata}.")
                    val attestation = pseudonym.createAttestation(credential.metadata, this.myPeer.key as PrivateKey)
                    pseudonym.addAttestation(this.myPeer.publicKey, attestation)
                    val payload = AttestPayload(attestation.getPlaintextSigned())
                    this.endpoint.send(peer, serializePacket(ATTEST_PAYLOAD, payload))
                }
            }

            for (attributeHash in requiredAttributes) {
                if (!knownAttributes.contains(attributeHash)) {
                    logger.info("Missing information for attestation ${attributeHash.bytes.toHex()}, requesting more.")
                    val payload = RequestMissingPayload(pseudonym.tree.elements.size)
                    this.endpoint.send(peer, serializePacket(REQUEST_MISSING_PAYLOAD, payload))
                }
            }

        } else {
            logger.warn("Received unsolicited disclosure from $peer, dropping.")
        }
    }

    fun requestAttestationAdvertisement(
        peer: Peer,
        attributeHash: ByteArray,
        name: String,
        blockType: String = ID_METADATA,
        metadata: HashMap<String, String>?,
    ) {
        val credential = this.selfAdvertise(attributeHash, name, blockType, metadata)
        this.permissions[peer] = this.tokenChain.size
        val disclosure = this.pseudonymManager.discloseCredentials(listOf(credential), setOf())
        val (metadataObj, tokens, attestations, authorities) = this.fitDisclosure(disclosure)
        val payload = DisclosePayload(metadataObj, tokens, attestations, authorities)
        this.endpoint.send(peer, serializePacket(DISCLOSURE_PAYLOAD, payload))
    }

    fun selfAdvertise(
        attributeHash: ByteArray,
        name: String,
        blockType: String,
        metadata: HashMap<String, String>?,
    ): Credential {
        val hash = if (attributeHash.size == 20) padSHA1Hash(attributeHash) else attributeHash

        val extendedMetadata =
            hashMapOf<String, Any>("name" to name, "schema" to blockType, "date" to System.currentTimeMillis() / 1000F)
        if (metadata != null) {
            extendedMetadata.putAll(metadata)
        }

        val credential = this.pseudonymManager.createCredential(
            hash,
            extendedMetadata,
            if (this.metadataChain.isNotEmpty()) this.metadataChain.last() else null
        )

        this.attestationChain += credential.attestations
        this.metadataChain += credential.metadata
        this.tokenChain += this.pseudonymManager.tree.elements[credential.metadata.tokenPointer.toKey()]!!

        return credential
    }

    private fun onDisclosure(peer: Peer, payload: DisclosePayload) {
        this.receivedDisclosureForAttest(
            peer,
            Disclosure(payload.metadata, payload.tokens, payload.attestations, payload.authorities)
        )
    }

    private fun onAttest(peer: Peer, payload: AttestPayload) {
        val attestation = IdentityAttestation.deserialize(payload.attestation, peer.publicKey)
        if (this.pseudonymManager.addAttestation(peer.publicKey, attestation)) {
            logger.info("Received attestation from ${peer.mid}.")
        } else {
            logger.warn("Received invalid attestation from ${peer.mid}.")
        }
    }

    private fun onRequestMissing(peer: Peer, payload: RequestMissingPayload) {
        var out = byteArrayOf()
        val permitted = this.tokenChain.subList(0, this.permissions.get(peer) ?: 0)
        permitted.forEachIndexed { index, token ->
            if (index >= payload.known) {
                val serialized = token.getPlaintextSigned()
                if (out.size + serialized.size > SAFE_UDP_PACKET_LENGTH) {
                    return@forEachIndexed
                }
                out += serialized
            }
        }
        val responsePayload = MissingResponsePayload(out)
        this.endpoint.send(peer, serializePacket(MISSING_RESPONSE_PAYLOAD, responsePayload))
    }

    private fun onMissingResponse(peer: Peer, payload: MissingResponsePayload) {
        this.receivedDisclosureForAttest(peer, Disclosure(byteArrayOf(), payload.tokens, byteArrayOf(), byteArrayOf()))
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as IdentityCommunity

        if (serviceId != other.serviceId) return false

        return true
    }

    override fun hashCode(): Int {
        return serviceId.hashCode()
    }

    companion object {
        const val SERVICE_ID = "d5889074c1e4c50423cdb6e9307ee0ca5695ead7"
    }

    class Factory(
        private val myPeer: Peer,
        private val identityManager: IdentityManager,
        private val database: IdentityStore,
        private val rendezvousId: String? = null,
        private val network: Network? = null
    ) : Overlay.Factory<IdentityCommunity>(IdentityCommunity::class.java) {
        override fun create(): IdentityCommunity {
            return if (rendezvousId != null) {
                if (network != null) {
                    IdentityCommunity(myPeer, identityManager, database, rendezvousId, network)
                } else {
                    IdentityCommunity(myPeer, identityManager, database, rendezvousId)
                }
            } else {
                if (network != null) {
                    IdentityCommunity(myPeer, identityManager, database, network = network)
                } else {
                    IdentityCommunity(myPeer, identityManager, database)
                }
            }
        }
    }
}

fun createCommunity(
    privateKey: PrivateKey,
    ipv8: IPv8,
    identityManager: IdentityManager,
    database: IdentityStore,
    rendezvousToken: ByteArray?,
): IdentityCommunity {
    val myPeer = Peer(privateKey)
    val network = Network()
    var rendezvousId: String? = null
    if (rendezvousToken != null) {
        rendezvousId =
            IdentityCommunity.SERVICE_ID.mapIndexed { i, c -> if (i < rendezvousToken.size) (c.toInt() xor rendezvousToken[i].toInt()).toChar() else c }
                .joinToString("")
    }

    val randomWalk = RandomWalk.Factory(timeout = 3.0, peers = 20)
    val config = SecondaryOverlayConfiguration(
        IdentityCommunity.Factory(myPeer, identityManager, database, rendezvousId, network),
        listOf(randomWalk),
        myPeer = myPeer,
        network = network
    )

    return ipv8.addSecondaryOverlayStrategy(config) as IdentityCommunity
}
