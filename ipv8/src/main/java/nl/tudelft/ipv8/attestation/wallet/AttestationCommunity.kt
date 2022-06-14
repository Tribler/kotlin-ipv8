package nl.tudelft.ipv8.attestation.wallet

import kotlinx.coroutines.Deferred
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import mu.KotlinLogging
import nl.tudelft.ipv8.Community
import nl.tudelft.ipv8.IPv4Address
import nl.tudelft.ipv8.Overlay
import nl.tudelft.ipv8.Peer
import nl.tudelft.ipv8.attestation.wallet.cryptography.IdentityAlgorithm
import nl.tudelft.ipv8.attestation.common.RequestCache
import nl.tudelft.ipv8.attestation.common.SchemaManager
import nl.tudelft.ipv8.attestation.common.consts.SchemaConstants.ID_METADATA
import nl.tudelft.ipv8.attestation.wallet.caches.*
import nl.tudelft.ipv8.attestation.wallet.consts.Metadata.ATTRIBUTE
import nl.tudelft.ipv8.attestation.wallet.consts.Metadata.ID_FORMAT
import nl.tudelft.ipv8.attestation.wallet.consts.Metadata.PROPOSED_VALUE
import nl.tudelft.ipv8.attestation.wallet.consts.Metadata.PUBLIC_KEY
import nl.tudelft.ipv8.attestation.wallet.cryptography.bonehexact.BonehPrivateKey
import nl.tudelft.ipv8.attestation.wallet.payloads.*
import nl.tudelft.ipv8.attestation.wallet.consts.PayloadIds.ATTESTATION
import nl.tudelft.ipv8.attestation.wallet.consts.PayloadIds.ATTESTATION_REQUEST
import nl.tudelft.ipv8.attestation.wallet.consts.PayloadIds.CHALLENGE
import nl.tudelft.ipv8.attestation.wallet.consts.PayloadIds.CHALLENGE_RESPONSE
import nl.tudelft.ipv8.attestation.wallet.consts.PayloadIds.VERIFY_ATTESTATION_REQUEST
import nl.tudelft.ipv8.attestation.wallet.cryptography.WalletAttestation
import nl.tudelft.ipv8.attestation.wallet.store.AttestationStore
import nl.tudelft.ipv8.messaging.EndpointAggregator
import nl.tudelft.ipv8.messaging.Packet
import nl.tudelft.ipv8.messaging.deserializeVarLen
import nl.tudelft.ipv8.messaging.payload.GlobalTimeDistributionPayload
import nl.tudelft.ipv8.messaging.serializeVarLen
import nl.tudelft.ipv8.peerdiscovery.Network
import nl.tudelft.ipv8.util.*
import org.json.JSONObject
import java.math.BigInteger
import java.util.concurrent.locks.ReentrantLock
import kotlin.collections.HashMap
import kotlin.random.Random
import kotlin.random.nextUBytes

private val logger = KotlinLogging.logger {}
private const val CHUNK_SIZE = 800

/**
 * Community for signing and verifying attestations.
 */
class AttestationCommunity(val database: AttestationStore) :
    Community() {

    override val serviceId = "e5d116f803a916a84850b9057cc0f662163f71f5"

    @Suppress("JoinDeclarationAndAssignment", "JoinDeclarationAndAssignment", "LateinitVarOverridesLateinitVar")
    override lateinit var myPeer: Peer

    @Suppress("JoinDeclarationAndAssignment", "JoinDeclarationAndAssignment", "LateinitVarOverridesLateinitVar")
    override lateinit var endpoint: EndpointAggregator

    @Suppress("JoinDeclarationAndAssignment", "JoinDeclarationAndAssignment", "LateinitVarOverridesLateinitVar")
    override lateinit var network: Network

    constructor(
        myPeer: Peer,
        endpoint: EndpointAggregator,
        network: Network,
        database: AttestationStore,
    ) : this(
        database
    ) {
        this.myPeer = myPeer
        this.endpoint = endpoint
        this.network = network
    }

    private val receiveBlockLock = ReentrantLock()
    val schemaManager = SchemaManager()

    private lateinit var attestationRequestCallback: (peer: Peer, attributeName: String, metaData: String, proposedValue: String?) -> Deferred<ByteArray?>
    private lateinit var attestationRequestCompleteCallback: (forPeer: Peer, attributeName: String, attestation: WalletAttestation, attestationHash: ByteArray, idFormat: String, fromPeer: Peer?, value: ByteArray?) -> Unit
    private lateinit var verifyRequestCallback: (peer: Peer, attributeHash: ByteArray) -> Deferred<Boolean?>
    private lateinit var attestationChunkCallback: (peer: Peer, sequenceNumber: Int) -> Unit

    private val attestationKeys: MutableMap<ByteArrayKey, Pair<BonehPrivateKey, String>> =
        mutableMapOf()

    private val cachedAttestationBlobs: MutableMap<ByteArrayKey, WalletAttestation> = mutableMapOf()
    private val allowedAttestations: MutableMap<String, List<ByteArray>> = mutableMapOf()

    val requestCache = RequestCache()

    init {
        schemaManager.registerDefaultSchemas()
        for (att in this.database.getAllAttestations()) {
            val hash = att.attestationHash
            val key = att.key
            val idFormat = att.idFormat
            this.attestationKeys[hash.toKey()] =
                Pair(this.getIdAlgorithm(idFormat).loadSecretKey(key), idFormat)
        }

        messageHandlers[VERIFY_ATTESTATION_REQUEST] = ::onVerifyAttestationRequestWrapper
        messageHandlers[ATTESTATION] = ::onAttestationChunkWrapper
        messageHandlers[CHALLENGE] = ::onChallengeWrapper
        messageHandlers[CHALLENGE_RESPONSE] = ::onChallengeResponseWrapper
        messageHandlers[ATTESTATION_REQUEST] = ::onRequestAttestationWrapper
    }

    /**
     * Method for requesting attestation with name [attributeName] from peer [peer] with additional metadata [metadata].
     * The attestation will be signed for the public-key belong to [privateKey].
     * An additional proposed value [proposedValue] can also be specified.
     */
    fun requestAttestation(
        peer: Peer,
        attributeName: String,
        privateKey: BonehPrivateKey,
        metadata: HashMap<String, String> = hashMapOf(),
        proposedValue: String? = null,
    ) {
        logger.info("Sending attestation request $attributeName to peer ${peer.mid}.")
        val publicKey = privateKey.publicKey()
        val inputMetadata = JSONObject(metadata)
        val idFormat = (inputMetadata.remove(ID_FORMAT) ?: ID_METADATA) as String

        val metadataJson = JSONObject()
        metadataJson.put(ATTRIBUTE, attributeName)
        metadataJson.put(
            PUBLIC_KEY,
            defaultEncodingUtils.encodeBase64ToString(publicKey.serialize())
        )
        metadataJson.put(ID_FORMAT, idFormat)
        // Will not be added if null.
        metadataJson.put(PROPOSED_VALUE, proposedValue)

        inputMetadata.keys().forEach {
            metadataJson.put(it, inputMetadata.get(it))
        }

        val globalTime = claimGlobalTime()
        val payload = RequestAttestationPayload(metadataJson.toString())

        val gTimeStr = globalTime.toString().toByteArray()
        this.requestCache.add(
            ReceiveAttestationRequestCache(
                this,
                peer.mid + gTimeStr,
                privateKey,
                attributeName,
                idFormat,
            )
        )
        this.allowedAttestations[peer.mid] =
            (this.allowedAttestations[peer.mid] ?: emptyList()) + listOf(gTimeStr)

        val packet =
            serializePacket(
                ATTESTATION_REQUEST,
                payload,
                prefix = this.prefix,
                timestamp = globalTime
            )
        endpoint.send(peer, packet)
    }

    /**
     * Method for verifying an attestation with hash [attestationHash] belonging to a peer listening on the
     * address [socketAddress]. The value we believe the attestation has is incorporated in [values], with the
     * used schema [idFormat]. After verification [callback] is called.
     */
    fun verifyAttestationValues(
        socketAddress: IPv4Address,
        attestationHash: ByteArray,
        values: List<ByteArray>,
        callback: ((ByteArray, List<Double>) -> Unit),
        idFormat: String,
    ) {
        val algorithm = this.getIdAlgorithm(idFormat)

        fun onComplete(attestationHash: ByteArray, relativityMap: HashMap<Any, Any>) {
            callback(attestationHash, values.map { algorithm.certainty(it, relativityMap) })
        }

        this.requestCache.add(
            ProvingAttestationCache(
                this,
                attestationHash,
                idFormat,
                onComplete = ::onComplete
            )
        )
        this.createVerifyAttestationRequest(socketAddress, attestationHash, idFormat)
    }

    private suspend fun onRequestAttestation(
        peer: Peer,
        dist: GlobalTimeDistributionPayload,
        payload: RequestAttestationPayload,
    ) {
        val metadata = JSONObject(payload.metadata)

        val attribute = metadata.remove(ATTRIBUTE) as String
        val encodedPK = metadata.remove(PUBLIC_KEY) as String
        val idFormat = metadata.remove(ID_FORMAT) as String
        var proposedValue: String? = null
        // We cannot cast null to string, hence member checking.
        if (metadata.has(PROPOSED_VALUE)) {
            proposedValue = metadata.remove(PROPOSED_VALUE) as String
        }

        val metadataString = metadata.toString()
        val idAlgorithm = this.getIdAlgorithm(idFormat)

        val value =
            this.attestationRequestCallback(peer, attribute, metadataString, proposedValue).await()
        if (value == null) {
            logger.error("Failed to get value from callback")
            return
        }

        // Decode as UTF-8 ByteArray
        val publicKey =
            idAlgorithm.loadPublicKey(defaultEncodingUtils.decodeBase64FromString(encodedPK))

        val attestationBlob = idAlgorithm.attest(publicKey, value)
        val attestation =
            idAlgorithm.deserialize(attestationBlob, idFormat)

        if (this::attestationRequestCompleteCallback.isInitialized) {
            this.attestationRequestCompleteCallback(
                peer,
                attribute,
                attestation,
                attestation.getHash(),
                idFormat,
                null,
                value
            )
        }

        this.sendAttestation(
            peer.address,
            attestationBlob + serializeVarLen(value),
            dist.globalTime,
        )
    }

    private fun onAttestationComplete(
        deserialized: WalletAttestation,
        privateKey: BonehPrivateKey,
        peer: Peer,
        name: String,
        attestationHash: ByteArray,
        idFormat: String,
        value: ByteArray? = null,
    ) {
        this.attestationKeys[attestationHash.toKey()] = Pair(privateKey, idFormat)
        this.database.insertAttestation(
            deserialized,
            attestationHash,
            privateKey,
            idFormat,
            value
        )

        if (this::attestationRequestCompleteCallback.isInitialized) {
            this.attestationRequestCompleteCallback(
                this.myPeer,
                name,
                deserialized,
                attestationHash,
                idFormat,
                peer,
                value
            )
        }
    }

    private fun createVerifyAttestationRequest(
        socketAddress: IPv4Address,
        attestationHash: ByteArray,
        idFormat: String,
    ) {
        this.requestCache.add(ReceiveAttestationVerifyCache(this, attestationHash, idFormat))

        val payload = VerifyAttestationRequestPayload(attestationHash)
        val packet = serializePacket(VERIFY_ATTESTATION_REQUEST, payload, prefix = this.prefix)
        this.endpoint.send(socketAddress, packet)
    }

    private suspend fun onVerifyAttestationRequest(
        peer: Peer,
        payload: VerifyAttestationRequestPayload,
    ) {
        val hash = stripSHA1Padding(payload.hash)
        val attestationBlob = this.database.getAttestationBlobByHash(hash)
        if (attestationBlob == null) {
            logger.warn("Dropping verification request of unknown hash ${payload.hash.toHex()}!")
            return
        }

        if (attestationBlob.isEmpty()) {
            logger.warn("Attestation blob for verification is empty: ${payload.hash.toHex()}!")
        }

        val value = verifyRequestCallback(peer, hash).await()
        if (value == null || !value) {
            logger.info("Verify request callback returned false for $peer, ${payload.hash.toHex()}")
            return
        }

        val (privateKey, idFormat) = this.attestationKeys[hash.toKey()]!!
        val privateAttestation =
            schemaManager.deserializePrivate(privateKey, attestationBlob, idFormat)
        val publicAttestationBlob = privateAttestation.serialize()
        this.cachedAttestationBlobs[hash.toKey()] = privateAttestation
        this.sendAttestation(peer.address, publicAttestationBlob)
    }

    private fun sendAttestation(
        sockedAddress: IPv4Address,
        blob: ByteArray,
        globalTime: ULong? = null,
    ) {
        var sequenceNumber = 0
        for (i in blob.indices step CHUNK_SIZE) {
            val endIndex = if (i + CHUNK_SIZE > blob.size) blob.size else i + CHUNK_SIZE

            val blobChunk = blob.copyOfRange(i, endIndex)
            logger.info("Sending attestation chunk $sequenceNumber to $sockedAddress")

            val payload = AttestationChunkPayload(sha1(blob), sequenceNumber, blobChunk)
            val packet =
                serializePacket(ATTESTATION, payload, prefix = this.prefix, timestamp = globalTime)
            this.endpoint.send(sockedAddress, packet)
            sequenceNumber += 1
        }
    }

    private fun onAttestationChunk(
        peer: Peer,
        dist: GlobalTimeDistributionPayload,
        payload: AttestationChunkPayload
    ) {
        if (this::attestationChunkCallback.isInitialized) {
            this.attestationChunkCallback(peer, payload.sequenceNumber)
        }
        val hashId = HashCache.idFromHash(ATTESTATION_VERIFY_PREFIX, payload.hash)
        val peerIds = mutableListOf<Pair<String, BigInteger>>()
        val allowedGlobs = this.allowedAttestations[peer.mid] ?: listOf()
        allowedGlobs.forEach {
            if (it.contentEquals(dist.globalTime.toString().toByteArray())) {
                peerIds.add(
                    PeerCache.idFromAddress(
                        ATTESTATION_REQUEST_PREFIX,
                        peer.mid + it
                    )
                )
            }
        }
        if (this.requestCache.has(hashId)) {
            val cache = this.requestCache.get(hashId) as ReceiveAttestationVerifyCache
            cache.attestationMap.add(Pair(payload.sequenceNumber, payload.data))

            var serialized = byteArrayOf()
            for ((_, chunk) in cache.attestationMap.sortedBy { attestation -> attestation.first }) {
                serialized += chunk
            }

            if (sha1(serialized).contentEquals(payload.hash)) {
                val attestation = schemaManager.deserialize(serialized, cache.idFormat)
                this.requestCache.pop(hashId)
                this.onReceivedAttestation(
                    peer,
                    attestation,
                    payload.hash
                )
            }

            logger.info("Received attestation chunk ${payload.sequenceNumber} for proving by ${peer.mid}")
        } else {
            var handled = false
            for (peerId in peerIds) {
                if (this.requestCache.has(peerId.first, peerId.second)) {
                    var cache = this.requestCache.get(
                        peerId.first,
                        peerId.second
                    ) as ReceiveAttestationRequestCache
                    cache.attestationMap.add(Pair(payload.sequenceNumber, payload.data))

                    var serialized = byteArrayOf()
                    for ((_, chunk) in cache.attestationMap.sortedBy { attestation -> attestation.first }) {
                        serialized += chunk
                    }

                    if (sha1(serialized).contentEquals(payload.hash)) {
                        val attestation =
                            schemaManager.deserializePrivate(
                                cache.privateKey,
                                serialized,
                                cache.idFormat
                            )
                        val value = deserializeVarLen(
                            serialized.copyOfRange(
                                attestation.serialize().size,
                                serialized.size
                            )
                        ).first
                        cache = (this.requestCache.pop(
                            peerId.first,
                            peerId.second
                        ) as ReceiveAttestationRequestCache)

                        this.allowedAttestations[peer.mid] =
                            this.allowedAttestations[peer.mid]!!.mapNotNull {
                                if (!it.contentEquals(
                                        dist.globalTime.toString().toByteArray()
                                    )
                                ) it else null
                            }
                        if (this.allowedAttestations[peer.mid].isNullOrEmpty()) {
                            this.allowedAttestations.remove(peer.mid)
                        }

                        this.onAttestationComplete(
                            attestation,
                            cache.privateKey,
                            peer,
                            cache.name,
                            attestation.getHash(),
                            cache.idFormat,
                            value,
                        )
                    }

                    logger.info("Received attestation chunk ${payload.sequenceNumber} for my attribute ${cache.name}")
                    handled = true
                }
            }
            if (!handled) {
                logger.warn("Received Attestation chunk which we did not request!")
            }
        }
    }

    private fun onReceivedAttestation(
        peer: Peer,
        attestation: WalletAttestation,
        attestationHash: ByteArray,
    ) {
        val algorithm = this.getIdAlgorithm(attestation.idFormat!!)

        val relativityMap = algorithm.createCertaintyAggregate(attestation)
        val hashedChallenges = arrayListOf<ByteArray>()
        val id = HashCache.idFromHash(PROVING_ATTESTATION_PREFIX, attestationHash)
        val cache =
            this.requestCache.get(id.first, id.second) as ProvingAttestationCache
        cache.publicKey = attestation.publicKey

        val challenges = algorithm.createChallenges(attestation.publicKey, attestation)
        for (challenge in challenges) {
            val challengeHash = sha1(challenge)
            hashedChallenges.add(challengeHash)
        }

        cache.relativityMap = relativityMap
        cache.hashedChallenges = hashedChallenges
        cache.challenges = challenges
        logger.info("Sending ${challenges.size} challenges to ${peer.mid}.")

        var remaining = 10
        for (challenge in challenges) {
            val (prefix, number) = HashCache.idFromHash(PENDING_CHALLENGES_PREFIX, sha1(challenge))
            if (remaining <= 0) {
                break
            } else if (this.requestCache.has(prefix, number)) {
                continue
            }

            remaining -= 1
            this.requestCache.add(
                PendingChallengesCache(
                    this,
                    sha1(challenge),
                    cache,
                    cache.idFormat
                )
            )

            val payload = ChallengePayload(attestationHash, challenge)
            val packet = serializePacket(CHALLENGE, payload, prefix = this.prefix)
            this.endpoint.send(peer.address, packet)
        }
    }

    private fun onChallenge(peer: Peer, payload: ChallengePayload) {
        if (!this.attestationKeys.containsKey(payload.attestationHash.toKey())) {
            logger.error("Received ChallengePayload $payload for unknown attestation hash ${payload.attestationHash.toHex()}.")
            return
        }

        val (privateKey, idFormat) = this.attestationKeys[payload.attestationHash.toKey()]!!
        val challengeHash = sha1(payload.challenge)
        val algorithm = this.getIdAlgorithm(idFormat)
        val attestation = this.cachedAttestationBlobs[payload.attestationHash.toKey()]!!

        val outGoingPayload = ChallengeResponsePayload(
            challengeHash,
            algorithm.createChallengeResponse(privateKey, attestation, payload.challenge)
        )
        val packet = serializePacket(CHALLENGE_RESPONSE, outGoingPayload, prefix = this.prefix)
        this.endpoint.send(peer.address, packet)
    }

    private fun onChallengeResponse(
        peer: Peer,
        payload: ChallengeResponsePayload,
    ) {
        synchronized(receiveBlockLock) {
            val (prefix, number) = HashCache.idFromHash(
                PENDING_CHALLENGES_PREFIX,
                payload.challengeHash
            )
            if (this.requestCache.has(prefix, number)) {
                val cache = this.requestCache.pop(prefix, number) as PendingChallengesCache
                val provingCache = cache.provingCache
                val (provingCachePrefix, provingCacheId) = HashCache.idFromHash(
                    PROVING_ATTESTATION_PREFIX,
                    provingCache.cacheHash
                )
                var challenge: ByteArray? = null

                // TODO: Come up with more elegant solution for ByteArray checking.
                val hashElement =
                    provingCache.hashedChallenges.find { x -> x.contentEquals(payload.challengeHash) }
                if (hashElement != null) {
                    provingCache.hashedChallenges.remove(hashElement)
                    for (c in provingCache.challenges) {
                        if (sha1(c).contentEquals(payload.challengeHash)) {
                            provingCache.challenges.remove(provingCache.challenges.first { x ->
                                x.contentEquals(
                                    c
                                )
                            })
                            challenge = c
                            break
                        }
                    }
                }
                val algorithm = this.getIdAlgorithm(provingCache.idFormat)
                if (cache.honestyCheck < 0) {
                    algorithm.processChallengeResponse(
                        provingCache.relativityMap,
                        challenge,
                        payload.response
                    )
                } else if (!algorithm.processHonestyChallenge(
                        cache.honestyCheck,
                        payload.response
                    )
                ) {
                    logger.error("${peer.address} attempted to cheat in the ZKP!")
                    if (this.requestCache.has(provingCachePrefix, provingCacheId)) {
                        this.requestCache.pop(provingCachePrefix, provingCacheId)
                    }
                    provingCache.attestationCallbacks(
                        provingCache.cacheHash,
                        algorithm.createCertaintyAggregate(null)
                    )
                }

                if (provingCache.hashedChallenges.isEmpty()) {
                    logger.info("Completed attestation verification.")
                    // TODO: We can most likely directly call pop.
                    if (this.requestCache.has(provingCachePrefix, provingCacheId)) {
                        this.requestCache.pop(provingCachePrefix, provingCacheId)
                    }
                    provingCache.attestationCallbacks(
                        provingCache.cacheHash,
                        provingCache.relativityMap
                    )
                } else {
                    // TODO: Add secure random.
                    val honestyCheck =
                        algorithm.honestCheck && (Random.nextUBytes(1)[0].toInt() < 38)
                    var honestyCheckByte = if (honestyCheck) arrayOf(0, 1, 2).random() else -1
                    challenge = null
                    if (honestyCheck) {
                        while (challenge == null || this.requestCache.has(
                                HashCache.idFromHash(
                                    PENDING_CHALLENGES_PREFIX,
                                    sha1(challenge)
                                )
                            )
                        ) {
                            challenge = algorithm.createHonestyChallenge(
                                provingCache.publicKey!!,
                                honestyCheckByte
                            )
                        }
                    }
                    if (!honestyCheck || (challenge != null && this.requestCache.has(
                            HashCache.idFromHash(
                                PENDING_CHALLENGES_PREFIX,
                                sha1(challenge)
                            )
                        ))
                    ) {
                        honestyCheckByte = -1
                        challenge = null
                        for (c in provingCache.challenges) {
                            if (!this.requestCache.has(
                                    HashCache.idFromHash(
                                        PENDING_CHALLENGES_PREFIX,
                                        sha1(c)
                                    )
                                )
                            ) {
                                challenge = c
                                break
                            }
                        }
                        if (challenge == null) {
                            logger.info("No more bit-pairs to challenge!")
                            return
                        }
                    }
                    logger.info("Sending challenge $honestyCheckByte (${provingCache.hashedChallenges.size}).")
                    this.requestCache.add(
                        PendingChallengesCache(
                            this,
                            sha1(challenge!!),
                            provingCache,
                            cache.idFormat,
                            honestyCheckByte
                        )
                    )

                    val outGoingPayload = ChallengePayload(provingCache.cacheHash, challenge)
                    val packet = serializePacket(CHALLENGE, outGoingPayload, prefix = this.prefix)
                    this.endpoint.send(peer.address, packet)
                }
            }
        }
    }

    private fun onRequestAttestationWrapper(packet: Packet) {
        val (peer, dist, payload) = packet.getAuthPayloadWithDist(RequestAttestationPayload.Deserializer)
        logger.info("Received RequestAttestation from ${peer.mid} for metadata ${payload.metadata}.")
        GlobalScope.launch { onRequestAttestation(peer, dist, payload) }
    }

    private fun onChallengeResponseWrapper(packet: Packet) {
        val (peer, payload) = packet.getAuthPayload(ChallengeResponsePayload.Deserializer)
        logger.info(
            "Received ChallengeResponse from ${peer.mid} for hash ${payload.challengeHash.toHex()} with response ${
                String(payload.response)
            }."
        )
        this.onChallengeResponse(peer, payload)
    }

    private fun onChallengeWrapper(packet: Packet) {
        val (peer, payload) = packet.getAuthPayload(ChallengePayload.Deserializer)
        logger.info(
            "Received Challenge from ${peer.mid} for hash ${payload.attestationHash.toHex()} with challenge ${
                String(payload.challenge)
            }."
        )
        this.onChallenge(peer, payload)
    }

    private fun onAttestationChunkWrapper(packet: Packet) {
        val (peer, dist, payload) = packet.getAuthPayloadWithDist(AttestationChunkPayload.Deserializer)
        logger.info("Received AttestationChunk from ${peer.mid} with sequence number ${payload.sequenceNumber}, data ${payload.data.size} bytes, hash ${payload.hash.toHex()}.")
        this.onAttestationChunk(peer, dist, payload)
    }

    private fun onVerifyAttestationRequestWrapper(packet: Packet) {
        val (peer, payload) = packet.getAuthPayload(VerifyAttestationRequestPayload.Deserializer)
        logger.info("Received VerifyAttestationRequest from ${peer.mid} for hash ${payload.hash.toHex()}.")
        GlobalScope.launch { onVerifyAttestationRequest(peer, payload) }
    }

    fun setAttestationRequestCallback(f: (peer: Peer, attributeName: String, metaData: String, proposedValue: String?) -> Deferred<ByteArray?>) {
        this.attestationRequestCallback = f
    }

    fun setAttestationRequestCompleteCallback(f: (forPeer: Peer, attributeName: String, attestation: WalletAttestation, attributeHash: ByteArray, idFormat: String, fromPeer: Peer?, value: ByteArray?) -> Unit) {
        this.attestationRequestCompleteCallback = f
    }

    fun setVerifyRequestCallback(f: (attributeName: Peer, attributeHash: ByteArray) -> Deferred<Boolean?>) {
        this.verifyRequestCallback = f
    }

    @Suppress("unused")
    fun setAttestationChunkCallback(f: (peer: Peer, sequenceNumber: Int) -> Unit) {
        this.attestationChunkCallback = f
    }

    fun getIdAlgorithm(idFormat: String): IdentityAlgorithm {
        return this.schemaManager.getAlgorithmInstance(idFormat)
    }

    class Factory(
        private val database: AttestationStore,
    ) : Overlay.Factory<AttestationCommunity>(AttestationCommunity::class.java) {
        override fun create(): AttestationCommunity {
            return AttestationCommunity(database)
        }
    }
}


