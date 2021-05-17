package nl.tudelft.ipv8.attestation.identity

import mu.KotlinLogging
import nl.tudelft.ipv8.*
import nl.tudelft.ipv8.attestation.common.RequestCache
import nl.tudelft.ipv8.attestation.common.consts.SchemaConstants.ID_METADATA
import nl.tudelft.ipv8.attestation.communication.caches.DisclosureRequestCache
import nl.tudelft.ipv8.attestation.communication.caches.TokenRequestCache
import nl.tudelft.ipv8.attestation.identity.consts.Metadata.DATE
import nl.tudelft.ipv8.attestation.identity.consts.Metadata.ID
import nl.tudelft.ipv8.attestation.identity.consts.Metadata.NAME
import nl.tudelft.ipv8.attestation.identity.consts.Metadata.SCHEMA
import nl.tudelft.ipv8.attestation.identity.consts.Metadata.VALUE
import nl.tudelft.ipv8.attestation.identity.consts.PayloadIds.ATTEST_PAYLOAD
import nl.tudelft.ipv8.attestation.identity.consts.PayloadIds.DISCLOSURE_PAYLOAD
import nl.tudelft.ipv8.attestation.identity.consts.PayloadIds.MISSING_RESPONSE_PAYLOAD
import nl.tudelft.ipv8.attestation.identity.consts.PayloadIds.REQUEST_MISSING_PAYLOAD
import nl.tudelft.ipv8.attestation.identity.datastructures.IdentityAttestation
import nl.tudelft.ipv8.attestation.identity.datastructures.Metadata
import nl.tudelft.ipv8.attestation.identity.store.Credential
import nl.tudelft.ipv8.attestation.identity.store.IdentityStore
import nl.tudelft.ipv8.attestation.identity.manager.Disclosure
import nl.tudelft.ipv8.attestation.identity.manager.IdentityManager
import nl.tudelft.ipv8.attestation.identity.manager.PseudonymManager
import nl.tudelft.ipv8.attestation.identity.payloads.AttestPayload
import nl.tudelft.ipv8.attestation.identity.payloads.DisclosePayload
import nl.tudelft.ipv8.attestation.identity.payloads.MissingResponsePayload
import nl.tudelft.ipv8.attestation.identity.payloads.RequestMissingPayload
import nl.tudelft.ipv8.attestation.identity.datastructures.tokenTree.Token
import nl.tudelft.ipv8.keyvault.PrivateKey
import nl.tudelft.ipv8.keyvault.PublicKey
import nl.tudelft.ipv8.messaging.Packet
import nl.tudelft.ipv8.peerdiscovery.Network
import nl.tudelft.ipv8.peerdiscovery.strategy.RandomWalk
import nl.tudelft.ipv8.util.ByteArrayKey
import nl.tudelft.ipv8.util.asMap
import nl.tudelft.ipv8.util.hexToBytes
import nl.tudelft.ipv8.util.padSHA1Hash
import nl.tudelft.ipv8.util.toHex
import nl.tudelft.ipv8.util.toKey
import org.json.JSONObject
import kotlin.math.max

const val SAFE_UDP_PACKET_LENGTH = 1296
const val DEFAULT_TIME_OUT = 300
const val TOKEN_SIZE = 64

val DEFAULT_METADATA = listOf(NAME, DATE, SCHEMA)

class HashInformation(
    val name: String,
    val value: ByteArray?,
    val time: Float,
    val publicKey: PublicKey,
    val metadata: HashMap<String, String>?,
)

private val logger = KotlinLogging.logger {}

/**
 * Community for signing metadata over attestations, allowing for chains of attestations.
 */
class IdentityCommunity(
    override var myPeer: Peer,
    identityManager: IdentityManager? = null,
    database: IdentityStore,
    override var serviceId: String = SERVICE_ID,
    override var network: Network = Network(),
) : Community() {

    var identityManager: IdentityManager = identityManager ?: IdentityManager(database)
    val requestCache = RequestCache()

    private lateinit var attestationPresentationCallback: (peer: Peer, attributeHash: ByteArray, value: ByteArray, metadata: Metadata, attestations: List<IdentityAttestation>, disclosureInformation: String) -> Unit
    private lateinit var attestationCallback: (peer: Peer, attestation: IdentityAttestation) -> Unit

    private val knownAttestationHashes = hashMapOf<ByteArrayKey, HashInformation>()
    private val pseudonymManager = this.identityManager.getPseudonym(this.myPeer.key)

    private var tokenChain: MutableList<Token> = mutableListOf()
    private var metadataChain: MutableList<Metadata> = mutableListOf()
    private var attestationChain: MutableList<IdentityAttestation> = mutableListOf()
    private val permissions: HashMap<Peer, Int> = hashMapOf()

    init {
        super.myPeer = myPeer
        super.network = network

        for (token in this.pseudonymManager.tree.elements.values) {
            val chain = this.pseudonymManager.tree.getRootPath(token).reversed()
            if (chain.size > this.tokenChain.size) {
                this.tokenChain = chain.toMutableList()
            }
        }
        for (credential in this.pseudonymManager.getCredentials()) {
            for (token in this.tokenChain) {
                if (credential.metadata.tokenPointer.contentEquals(token.hash)) {
                    this.metadataChain.add(credential.metadata)
                    this.attestationChain.addAll(credential.attestations)
                    break
                }
            }
        }

        messageHandlers[DISCLOSURE_PAYLOAD] = ::onDisclosureWrapper
        messageHandlers[ATTEST_PAYLOAD] = ::onAttestWrapper
        messageHandlers[REQUEST_MISSING_PAYLOAD] = ::onRequestMissingWrapper
        messageHandlers[MISSING_RESPONSE_PAYLOAD] = ::onMissingResponseWrapper
    }

    fun presentAttestationAdvertisement(
        peer: Peer,
        credential: Credential,
        presentationMetadata: String,
    ) {
        this.permissions[peer] = this.tokenChain.size
        val disclosure = this.pseudonymManager.discloseCredentials(listOf(credential), setOf())
        val (metadataObj, tokens, attestations, authorities) = this.fitDisclosure(disclosure)
        val payload =
            DisclosePayload(metadataObj, tokens, attestations, authorities, presentationMetadata)
        this.endpoint.send(peer, serializePacket(DISCLOSURE_PAYLOAD, payload))
    }

    fun advertiseAttestation(
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
            hashMapOf<String, Any>(
                NAME to name,
                SCHEMA to blockType,
                DATE to System.currentTimeMillis() / 1000F
            )
        if (metadata != null) {
            extendedMetadata.putAll(metadata)
        }

        val credential = this.pseudonymManager.createCredential(
            hash,
            extendedMetadata,
            if (this.metadataChain.isNotEmpty()) this.metadataChain.last() else null
        )

        this.attestationChain.addAll(credential.attestations)
        this.metadataChain.add(credential.metadata)
        this.tokenChain.add(pseudonymManager.tree.elements[credential.metadata.tokenPointer.toKey()]!!)

        return credential
    }

    fun addKnownHash(
        attributeHash: ByteArray,
        name: String,
        value: ByteArray?,
        publicKey: PublicKey,
        metadata: HashMap<String, String>? = null,
    ) {
        val hash = if (attributeHash.size == 20) padSHA1Hash(attributeHash) else attributeHash
        this.knownAttestationHashes[ByteArrayKey(hash)] =
            HashInformation(name, value, System.currentTimeMillis() / 1000F, publicKey, metadata)
    }

    fun getAttestationByHash(attributeHash: ByteArray): Metadata? {
        val hash = if (attributeHash.size == 20) padSHA1Hash(attributeHash) else attributeHash
        for (credential in this.pseudonymManager.getCredentials()) {
            val token =
                this.pseudonymManager.tree.elements[ByteArrayKey(credential.metadata.tokenPointer)]
            if (token?.contentHash.contentEquals(hash)) {
                return credential.metadata
            }
        }
        return null
    }

    private fun shouldSign(
        pseudonym: PseudonymManager,
        metadata: Metadata,
        isVerification: Boolean = false
    ): Boolean {
        val transaction = JSONObject(String(metadata.serializedMetadata))
        val requestedKeys = transaction.keySet()
        if (!pseudonym.tree.elements.containsKey(metadata.tokenPointer.toKey())) {
            logger.debug("Not signing $metadata, unknown token!")
            return false
        }
        val attributeHash = pseudonym.tree.elements[metadata.tokenPointer.toKey()]!!.contentHash
        if (NAME !in requestedKeys || DATE !in requestedKeys || SCHEMA !in requestedKeys) {
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
        if (!isVerification && System.currentTimeMillis() / 1000F > this.knownAttestationHashes[attributeHash.toKey()]?.time?.plus(
                (DEFAULT_TIME_OUT)
            ) ?: 0F
        ) {
            logger.debug("Not signing $metadata, timed out!")
            return false
        }
        if (transaction[NAME] != this.knownAttestationHashes[attributeHash.toKey()]?.name) {
            logger.debug("Not signing $metadata, name does not match!")
            return false
        }
        if (this.knownAttestationHashes[attributeHash.toKey()]!!.metadata != null
            && transaction.asMap().filterKeys { it !in DEFAULT_METADATA }
            != this.knownAttestationHashes[attributeHash.toKey()]!!.metadata!!
        ) {
            logger.debug("Not signing $metadata, metadata does not match!")
            return false
        }
        if (!isVerification) {
            for (attestation in pseudonym.database.getAttestationsOver(metadata)) {
                if (this.myPeer.publicKey.keyToBin()
                        .contentEquals(pseudonym.database.getAuthority(attestation))
                ) {
                    logger.debug("Not signing $metadata, already attested!")
                    return false
                }
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
            tokensOut = tokens.copyOfRange(0, (trimLength * tokenSize))
        }
        return Disclosure(metadata, tokensOut, attestations, authorities)
    }

    private fun receivedDisclosureForAttest(peer: Peer, disclosure: Disclosure) {
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
                if (shouldSign(pseudonym, credential.metadata)) {
                    logger.info("Attesting to ${credential.metadata}.")
                    val myPrivateKey = this.myPeer.key as PrivateKey
                    val attestation = pseudonym.createAttestation(
                        credential.metadata,
                        myPrivateKey
                    )
                    pseudonym.addAttestation(this.myPeer.publicKey, attestation)
                    val payload = AttestPayload(attestation.getPlaintextSigned())
                    this.endpoint.send(peer, serializePacket(ATTEST_PAYLOAD, payload))
                }
            }
        }

        for (attributeHash in requiredAttributes) {
            if (!knownAttributes.contains(attributeHash)) {
                logger.info("Missing information for attestation ${attributeHash.bytes.toHex()}, requesting more.")
                val payload = RequestMissingPayload(pseudonym.tree.elements.size)
                this.endpoint.send(peer, serializePacket(REQUEST_MISSING_PAYLOAD, payload))
            }
        }
    }

    // TODO: remove parameters from disclosureInformation.
    private fun receivedDisclosureForPresentation(
        peer: Peer,
        disclosure: Disclosure,
        attributeName: String,
        disclosureInformation: String
    ) {
        val (correct, pseudonym) = this.identityManager.substantiate(
            peer.publicKey,
            disclosure.metadata,
            disclosure.tokens,
            disclosure.attestations,
            disclosure.authorities
        )

        val disclosureJSON = JSONObject(disclosureInformation)
        val requiredAttributes =
            listOf(disclosureJSON.getString("attestationHash").hexToBytes().toKey())
        val knownAttributes: List<ByteArrayKey> =
            pseudonym.tree.elements.values.map { ByteArrayKey(it.contentHash) }

        if (correct && requiredAttributes.any { knownAttributes.contains(it) }) {
            for (credential in pseudonym.getCredentials()) {
                val value = disclosureJSON.getString(VALUE).hexToBytes()
                @Suppress("UNCHECKED_CAST")
                this.addKnownHash(
                    requiredAttributes[0].bytes,
                    attributeName,
                    value,
                    peer.publicKey,
                    JSONObject(String(credential.metadata.serializedMetadata)).toMap() as HashMap<String, String>
                )
                if (shouldSign(pseudonym, credential.metadata)) {
                    val presentedAttributeName =
                        JSONObject(String(credential.metadata.serializedMetadata)).getString(NAME)
                    if (presentedAttributeName != attributeName) {
                        logger.warn("Client sent wrong attestation. Requested: $attributeName, received: $presentedAttributeName")
                        return
                    }
                    logger.info("Received valid attestation presentation ${String(credential.metadata.serializedMetadata)}")

                    this.attestationPresentationCallback(
                        peer, requiredAttributes[0].bytes, value, credential.metadata,
                        credential.attestations.toList(),
                        disclosureInformation
                    )
                }
            }
        }

        for (attributeHash in requiredAttributes) {
            if (!knownAttributes.contains(attributeHash)) {
                logger.info("Missing information for attestation ${attributeHash.bytes.toHex()}, requesting more.")
                // TODO: add second parameter for uniqueness.
                requestCache.add(
                    TokenRequestCache(
                        requestCache,
                        peer.mid,
                        attributeName,
                        disclosureInformation
                    )
                )
                val payload = RequestMissingPayload(pseudonym.tree.elements.size)
                this.endpoint.send(peer, serializePacket(REQUEST_MISSING_PAYLOAD, payload))
            }
        }
    }

    private fun onDisclosure(peer: Peer, payload: DisclosePayload) {
        val isAttestationRequest =
            this.knownAttestationHashes.values.any { it.publicKey == peer.publicKey }
        val disclosureMD = JSONObject(payload.advertisementInformation ?: "{}")
        val id = disclosureMD.optString(ID)
        val idPair = DisclosureRequestCache.idFromUUID(id)
        val isAttestationPresentation = this.requestCache.has(idPair)

        when {
            // Presentation takes precedence, as id should not be set otherwise.
            isAttestationPresentation -> {
                val cache = this.requestCache.pop(idPair)!! as DisclosureRequestCache
                this.receivedDisclosureForPresentation(
                    peer,
                    Disclosure(
                        payload.metadata,
                        payload.tokens,
                        payload.attestations,
                        payload.authorities
                    ),
                    cache.disclosureRequest.attributeName,
                    payload.advertisementInformation!!
                )
            }
            isAttestationRequest -> {
                this.receivedDisclosureForAttest(
                    peer,
                    Disclosure(
                        payload.metadata,
                        payload.tokens,
                        payload.attestations,
                        payload.authorities
                    )
                )
            }
            else -> {
                logger.warn("Received unsolicited disclosure from ${peer.mid}, dropping.")
            }
        }
    }

    private fun onAttest(peer: Peer, payload: AttestPayload) {
        val attestation = IdentityAttestation.deserialize(payload.attestation, peer.publicKey)
        if (this.pseudonymManager.addAttestation(peer.publicKey, attestation)) {
            logger.info("Received attestation from ${peer.mid}.")
            if (this::attestationCallback.isInitialized) {
                this.attestationCallback(peer, attestation)
            }
        } else {
            logger.warn("Received invalid attestation from ${peer.mid}.")
        }
    }

    private fun onRequestMissing(peer: Peer, payload: RequestMissingPayload) {
        logger.info("Received missing request from ${peer.mid} for ${payload.known} tokens.")
        var out = byteArrayOf()
        val permitted = this.tokenChain.subList(0, this.permissions[peer] ?: 0)

        for ((index, token) in permitted.withIndex()) {
            if (index >= payload.known) {
                val serialized = token.getPlaintextSigned()
                if (out.size + serialized.size > SAFE_UDP_PACKET_LENGTH) {
                    break
                }
                out += serialized
            }
        }
        val responsePayload = MissingResponsePayload(out)
        this.endpoint.send(peer, serializePacket(MISSING_RESPONSE_PAYLOAD, responsePayload))
    }

    private fun onMissingResponse(peer: Peer, payload: MissingResponsePayload) {
        val solicitedAttestationRequest =
            this.knownAttestationHashes.values.any { it.publicKey == peer.publicKey }
        val idPair = TokenRequestCache.generateId(peer.mid)
        val solicitedAttestationPresentation = this.requestCache.has(idPair)

        when {
            solicitedAttestationPresentation -> {
                val cache = (this.requestCache.pop(idPair)!! as TokenRequestCache)
                this.receivedDisclosureForPresentation(
                    peer,
                    Disclosure(byteArrayOf(), payload.tokens, byteArrayOf(), byteArrayOf()),
                    cache.requestedAttributeName,
                    cache.disclosureInformation
                )
            }
            solicitedAttestationRequest -> {
                this.receivedDisclosureForAttest(
                    peer,
                    Disclosure(byteArrayOf(), payload.tokens, byteArrayOf(), byteArrayOf())
                )
            }

            else -> {
                logger.warn("Received unsolicited disclosure from $peer, dropping.")
            }
        }
    }

    fun setAttestationPresentationCallback(f: (peer: Peer, attributeHash: ByteArray, value: ByteArray, metadata: Metadata, attestations: List<IdentityAttestation>, disclosureInformation: String) -> Unit) {
        this.attestationPresentationCallback = f
    }

    fun setAttestationCallback(f: (peer: Peer, attestation: IdentityAttestation) -> Unit) {
        this.attestationCallback = f
    }

    private fun onDisclosureWrapper(packet: Packet) {
        val (peer, payload) = packet.getAuthPayload(DisclosePayload.Deserializer)
        logger.info("  Disclose payload from ${peer.mid}.")
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
