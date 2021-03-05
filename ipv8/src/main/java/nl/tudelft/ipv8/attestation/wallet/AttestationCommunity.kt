package nl.tudelft.ipv8.attestation.wallet

import mu.KotlinLogging
import nl.tudelft.ipv8.Community
import nl.tudelft.ipv8.IPv4Address
import nl.tudelft.ipv8.Overlay
import nl.tudelft.ipv8.Peer
import nl.tudelft.ipv8.attestation.WalletAttestation
import nl.tudelft.ipv8.attestation.IdentityAlgorithm
import nl.tudelft.ipv8.attestation.TrustedAuthorityManager
import nl.tudelft.ipv8.attestation.schema.*
import nl.tudelft.ipv8.attestation.wallet.AttestationCommunity.MessageId.ATTESTATION
import nl.tudelft.ipv8.attestation.wallet.AttestationCommunity.MessageId.ATTESTATION_REQUEST
import nl.tudelft.ipv8.attestation.wallet.AttestationCommunity.MessageId.CHALLENGE
import nl.tudelft.ipv8.attestation.wallet.AttestationCommunity.MessageId.CHALLENGE_RESPONSE
import nl.tudelft.ipv8.attestation.wallet.AttestationCommunity.MessageId.VERIFY_ATTESTATION_REQUEST
import nl.tudelft.ipv8.attestation.wallet.caches.*
import nl.tudelft.ipv8.attestation.wallet.cryptography.bonehexact.BonehPrivateKey
import nl.tudelft.ipv8.attestation.wallet.payloads.*
import nl.tudelft.ipv8.keyvault.PrivateKey
import nl.tudelft.ipv8.keyvault.PublicKey
import nl.tudelft.ipv8.messaging.Packet
import nl.tudelft.ipv8.messaging.payload.GlobalTimeDistributionPayload
import nl.tudelft.ipv8.util.*
import org.json.JSONObject
import java.math.BigInteger
import java.util.concurrent.locks.ReentrantLock
import kotlin.collections.ArrayList
import kotlin.collections.HashMap
import kotlin.random.Random
import kotlin.random.nextUBytes


private val logger = KotlinLogging.logger {}
private const val CHUNK_SIZE = 800

class AttestationCommunity(val database: AttestationStore) : Community() {
    override val serviceId = "e5d116f803a916a84850b9057cc0f662163f71f5"

    private val receiveBlockLock = ReentrantLock()
    val schemaManager = SchemaManager()

    private lateinit var attestationRequestCallback: (peer: Peer, attributeName: String, metaData: String) -> ByteArray
    private lateinit var attestationRequestCompleteCallback: (forPeer: Peer, attributeName: String, attestation: WalletAttestation, attestationHash: ByteArray, idFormat: String, fromPeer: Peer?, metaData: String?, signature: ByteArray?) -> Unit
    private lateinit var verifyRequestCallback: (peer: Peer, attributeHash: ByteArray) -> Boolean
    private lateinit var attestationChunkCallback: (peer: Peer, sequenceNumber: Int) -> Unit

    val attestationKeys: MutableMap<ByteArrayKey, Pair<BonehPrivateKey, String>> = mutableMapOf()

    private val cachedAttestationBlobs = mutableMapOf<ByteArrayKey, WalletAttestation>()
    private val allowedAttestations = mutableMapOf<String, Array<ByteArray>>()

    val trustedAuthorityManager = TrustedAuthorityManager(database)

    val requestCache = RequestCache()

    init {
        trustedAuthorityManager.loadAuthorities()
        schemaManager.registerDefaultSchemas()
        for (att in this.database.getAllAttestations()) {
            val hash = att.attestationHash
            val key = att.key
            val idFormat = att.idFormat
            this.attestationKeys[ByteArrayKey(hash)] = Pair(this.getIdAlgorithm(idFormat).loadSecretKey(key), idFormat)
        }

        messageHandlers[VERIFY_ATTESTATION_REQUEST] = ::onVerifyAttestationRequestWrapper
        messageHandlers[ATTESTATION] = ::onAttestationChunkWrapper
        messageHandlers[CHALLENGE] = ::onChallengeWrapper
        messageHandlers[CHALLENGE_RESPONSE] = ::onChallengeResponseWrapper
        messageHandlers[ATTESTATION_REQUEST] = ::onRequestAttestationWrapper
    }

    private fun onRequestAttestationWrapper(packet: Packet) {
        val (peer, dist, payload) = packet.getAuthPayloadWithDist(RequestAttestationPayload.Deserializer)
        logger.info("Received RequestAttestation from ${peer.mid} for metadata ${payload.metadata}.")
        this.onRequestAttestation(peer, dist, payload)
    }

    private fun onChallengeResponseWrapper(packet: Packet) {
        val (peer, _, payload) = packet.getAuthPayloadWithDist(ChallengeResponsePayload.Deserializer)
        logger.info("Received ChallengeResponse from ${peer.mid} for hash ${String(payload.challengeHash)} with response ${
            String(payload.response)
        }.")
        this.onChallengeResponse(peer, payload)
    }

    private fun onChallengeWrapper(packet: Packet) {
        val (peer, _, payload) = packet.getAuthPayloadWithDist(ChallengePayload.Deserializer)
        logger.info("Received Challenge from ${peer.mid} for hash ${String(payload.attestationHash)} with challenge ${
            String(payload.challenge)
        }.")
        this.onChallenge(peer, payload)
    }

    private fun onAttestationChunkWrapper(packet: Packet) {
        val (peer, dist, payload) = packet.getAuthPayloadWithDist(AttestationChunkPayload.Deserializer)
        logger.info("Received AttestationChunk from ${peer.mid} with sequence number ${payload.sequenceNumber}, data ${
            String(payload.data)
        } hash ${String(payload.hash)}.")
        this.onAttestationChunk(peer, dist, payload)
    }

    private fun onVerifyAttestationRequestWrapper(packet: Packet) {
        val (peer, _, payload) = packet.getAuthPayloadWithDist(VerifyAttestationRequestPayload.Deserializer)
        logger.info("Received VerifyAttestationRequest from ${peer.mid} for hash ${String(payload.hash)}.")
        this.onVerifyAttestationRequest(peer, payload)

    }

    private fun getIdAlgorithm(idFormat: String): IdentityAlgorithm {
        return this.schemaManager.getAlgorithmInstance(idFormat)
    }

    fun setAttestationRequestCallback(f: (peer: Peer, attributeName: String, metaData: String) -> ByteArray) {
        this.attestationRequestCallback = f
    }

    fun setAttestationRequestCompleteCallback(f: (forPeer: Peer, attributeName: String, attestation: WalletAttestation, attributeHash: ByteArray, idFormat: String, fromPeer: Peer?, metaData: String?, Signature: ByteArray?) -> Unit) {
        this.attestationRequestCompleteCallback = f
    }

    fun setVerifyRequestCallback(f: (attributeName: Peer, attributeHash: ByteArray) -> Boolean) {
        this.verifyRequestCallback = f
    }

    fun setAttestationChunkCallback(f: (peer: Peer, sequenceNumber: Int) -> Unit) {
        this.attestationChunkCallback = f
    }

    @Suppress("UNUSED_PARAMETER")
    fun dumbBlob(attributeName: String, idFormat: String, blob: ByteArray, metaData: String = "") {
        // TODO: Implement this method.
        throw NotImplementedError()
    }

    fun verifyAttestationLocally(
        peer: Peer,
        attestationHash: ByteArray,
        metadata: String,
        signature: ByteArray,
        attestorKey: PublicKey,
    ): Boolean {
        val parsedMetadata = JSONObject(metadata)
        val attesteeKeyHash = parsedMetadata.optString("trustchain_address_hash")
        attesteeKeyHash ?: return false

        val isTrusted = this.trustedAuthorityManager.contains(attestorKey.keyToHash().toHex())
        val isOwner = peer.publicKey.keyToHash().toHex() == attesteeKeyHash
        val isSignatureValid = attestorKey.verify(signature,
            sha1(attestationHash + metadata.toByteArray()))

        return isTrusted && isOwner && isSignatureValid
    }

    fun requestAttestation(
        peer: Peer,
        attributeName: String,
        privateKey: BonehPrivateKey,
        metadata: HashMap<String, String> = hashMapOf(),
        signature: Boolean = false,
    ) {
        logger.info("Sending attestation request $attributeName to peer ${peer.mid}.")
        val publicKey = privateKey.publicKey()
        val inputMetadata = JSONObject(metadata)
        val idFormat = inputMetadata.optString("id_format", "id_metadata")

        val metadataJson = JSONObject()
        metadataJson.put("attribute", attributeName)
        // Encode to UTF-8
        metadataJson.put("public_key", defaultEncodingUtils.encodeBase64ToString(publicKey.serialize()))
        metadataJson.putOpt("id_format", idFormat)
        metadataJson.putOpt("signature", signature)

        inputMetadata.keys().forEach {
            metadataJson.put(it, inputMetadata.get(it))
        }

        val globalTime = claimGlobalTime()
        val payload = RequestAttestationPayload(metadataJson.toString())

        val gTimeStr = globalTime.toString().toByteArray()
        this.requestCache.add(ReceiveAttestationRequestCache(this,
            peer.mid + gTimeStr,
            privateKey,
            attributeName,
            idFormat,
            signature))
        this.allowedAttestations[peer.mid] =
            (this.allowedAttestations[peer.mid] ?: emptyArray()) + arrayOf(gTimeStr)

        val packet =
            serializePacket(ATTESTATION_REQUEST,
                payload,
                prefix = this.prefix,
                timestamp = globalTime)
        endpoint.send(peer, packet)
    }

    private fun onRequestAttestation(
        peer: Peer,
        dist: GlobalTimeDistributionPayload,
        payload: RequestAttestationPayload,
    ) {
        val metadata = JSONObject(payload.metadata)
        val attribute = metadata.getString("attribute")
        var value = metadata.optString("value").toByteArray()
        val pubkeyEncoded = metadata.getString("public_key")

        val idFormat = metadata.getString("id_format")
        val idAlgorithm = this.getIdAlgorithm(idFormat)
        val shouldSign = metadata.optBoolean("signature", false)

        if (value.isEmpty()) {
            value = this.attestationRequestCallback(peer, attribute, payload.metadata)
        }

        val stringifiedValue = when (idFormat) {
            ID_METADATA_RANGE_18PLUS -> ID_METADATA_RANGE_18PLUS_PUBLIC_VALUE
            ID_METADATA_RANGE_UNDERAGE -> ID_METADATA_RANGE_UNDERAGE_PUBLIC_VALUE
            else -> String(value)
        }

        metadata.put("value", stringifiedValue)
        metadata.put("trustchain_address_hash", peer.publicKey.keyToHash().toHex())

        // Decode as UTF-8 ByteArray
        val publicKey = idAlgorithm.loadPublicKey(defaultEncodingUtils.decodeBase64FromString(pubkeyEncoded))
        val attestationBlob = idAlgorithm.attest(publicKey, value)
        val attestation =
            idAlgorithm.deserialize(attestationBlob, idFormat)

        val signableData = attestation.getHash() + metadata.toString().toByteArray()
        val signature = (myPeer.key as PrivateKey).sign(sha1(signableData))

        if (this::attestationRequestCompleteCallback.isInitialized) {
            this.attestationRequestCompleteCallback(
                peer,
                attribute,
                attestation,
                attestation.getHash(),
                idFormat,
                null,
                metadata.toString(),
                signature,
            )
        }

        this.sendAttestation(peer.address,
            attestationBlob,
            dist.globalTime,
            metadata.toString().toByteArray(),
            if (shouldSign) signature else null)
    }

    private fun onAttestationComplete(
        deserialized: WalletAttestation,
        privateKey: BonehPrivateKey,
        peer: Peer,
        name: String,
        attestationHash: ByteArray,
        idFormat: String,
        metaData: String? = null,
        signature: ByteArray? = null,
    ) {
        this.attestationKeys[ByteArrayKey(attestationHash)] = Pair(privateKey, idFormat)
        this.database.insertAttestation(deserialized,
            attestationHash,
            privateKey,
            idFormat,
            metaData,
            signature,
            peer.publicKey)

        if (this::attestationRequestCompleteCallback.isInitialized) {
            this.attestationRequestCompleteCallback(this.myPeer,
                name,
                deserialized,
                attestationHash,
                idFormat,
                peer,
                metaData,
                signature)
        }
    }

    fun verifyAttestationValues(
        socketAddress: IPv4Address,
        attestationHash: ByteArray,
        values: ArrayList<ByteArray>,
        callback: ((ByteArray, List<Double>) -> Unit),
        idFormat: String,
    ) {
        val algorithm = this.getIdAlgorithm(idFormat)

        fun onComplete(attestationHash: ByteArray, relativityMap: HashMap<Any, Any>) {
            callback(attestationHash, values.map { algorithm.certainty(it, relativityMap) })
        }

        this.requestCache.add(ProvingAttestationCache(this, attestationHash, idFormat, onComplete = ::onComplete))
        this.createVerifyAttestationRequest(socketAddress, attestationHash, idFormat)
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

    private fun onVerifyAttestationRequest(
        peer: Peer,
        payload: VerifyAttestationRequestPayload,
    ) {
        val attestationBlob = this.database.getAttestationByHash(payload.hash)
        if (attestationBlob == null) {
            logger.warn("Dropping verification request of unknown hash ${payload.hash}!")
            return
        }

        if (attestationBlob.isEmpty()) {
            logger.warn("Attestation blob for verification is empty: ${payload.hash}!")
        }

        val value = verifyRequestCallback(peer, payload.hash)
        if (!value) {
            logger.info("Verify request callback returned false for $peer, ${payload.hash}")
            return
        }

        val (privateKey, idFormat) = this.attestationKeys[ByteArrayKey(payload.hash)]!!
        val privateAttestation = schemaManager.deserializePrivate(privateKey, attestationBlob, idFormat)
        val publicAttestationBlob = privateAttestation.serialize()
        this.cachedAttestationBlobs[ByteArrayKey(payload.hash)] = privateAttestation
        this.sendAttestation(peer.address, publicAttestationBlob)
    }

    private fun sendAttestation(
        sockedAddress: IPv4Address,
        blob: ByteArray,
        globalTime: ULong? = null,
        metaData: ByteArray? = null,
        signature: ByteArray? = null,
    ) {
        var sequenceNumber = 0
        for (i in blob.indices step CHUNK_SIZE) {
            val endIndex = if (i + CHUNK_SIZE > blob.size) blob.size else i + CHUNK_SIZE

            val blobChunk = blob.copyOfRange(i, endIndex)
            logger.info("Sending attestation chunk $sequenceNumber to $sockedAddress")

            // Only send metadata and signature on final package to reduce overhead.
            val payload =
                if (i + CHUNK_SIZE > blob.size)
                    AttestationChunkPayload(sha1(blob), sequenceNumber, blobChunk, metaData, signature)
                else
                    AttestationChunkPayload(sha1(blob), sequenceNumber, blobChunk)
            val packet = serializePacket(ATTESTATION, payload, prefix = this.prefix, timestamp = globalTime)
            this.endpoint.send(sockedAddress, packet)
            sequenceNumber += 1
        }
    }

    private fun onAttestationChunk(peer: Peer, dist: GlobalTimeDistributionPayload, payload: AttestationChunkPayload) {
        if (this::attestationChunkCallback.isInitialized) {
            this.attestationChunkCallback(peer, payload.sequenceNumber)
        }
        val hashId = HashCache.idFromHash(ATTESTATION_VERIFY_PREFIX, payload.hash)
        val (prefix, number) = hashId
        val peerIds = arrayListOf<Pair<String, BigInteger>>()
        val allowedGlobs = this.allowedAttestations.get(peer.mid) ?: arrayOf()
        allowedGlobs.forEach {
            if (it.contentEquals(dist.globalTime.toString().toByteArray())) {
                peerIds.add(PeerCache.idFromAddress(ATTESTATION_REQUEST_PREFIX,
                    peer.mid + it))
            }
        }
        if (this.requestCache.has(prefix, number)) {
            val cache = this.requestCache.get(prefix, number) as ReceiveAttestationVerifyCache
            cache.attestationMap.add(Pair(payload.sequenceNumber, payload.data))

            var serialized = byteArrayOf()
            for ((_, chunk) in cache.attestationMap.sortedBy { attestation -> attestation.first }) {
                serialized += chunk
            }

            if (sha1(serialized).contentEquals(payload.hash)) {
                val deserialized = schemaManager.deserialize(serialized, cache.idFormat)
                this.requestCache.pop(prefix, number)
                this.onReceivedAttestation(peer,
                    deserialized,
                    payload.hash)
            }

            logger.info("Received attestation chunk ${payload.sequenceNumber} for proving by $peer")
        } else {
            var handled = false
            for (peerId in peerIds) {
                if (this.requestCache.has(peerId.first, peerId.second)) {
                    var cache = this.requestCache.get(peerId.first, peerId.second) as ReceiveAttestationRequestCache
                    cache.attestationMap.add(Pair(payload.sequenceNumber, payload.data))

                    var serialized = byteArrayOf()
                    for ((_, chunk) in cache.attestationMap.sortedBy { attestation -> attestation.first }) {
                        serialized += chunk
                    }

                    if (sha1(serialized).contentEquals(payload.hash)) {
                        val deserialized =
                            schemaManager.deserializePrivate(cache.privateKey, serialized, cache.idFormat)
                        cache = (this.requestCache.pop(peerId.first, peerId.second) as ReceiveAttestationRequestCache)

                        this.allowedAttestations[peer.mid] = this.allowedAttestations[peer.mid]!!.mapNotNull {
                            if (!it.contentEquals(dist.globalTime.toString().toByteArray())) it else null
                        }.toTypedArray()
                        if (this.allowedAttestations[peer.mid].isNullOrEmpty()) {
                            this.allowedAttestations.remove(peer.mid)
                        }

                        this.onAttestationComplete(
                            deserialized,
                            cache.privateKey,
                            peer,
                            cache.name,
                            deserialized.getHash(),
                            cache.idFormat,
                            if (payload.metadata != null) String(payload.metadata) else null,
                            if (payload.signature != null) payload.signature else null
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
        logger.info("Sending ${challenges.size} challenges to $peer.")

        var remaining = 10
        for (challenge in challenges) {
            val (prefix, number) = HashCache.idFromHash(PENDING_CHALLENGES_PREFIX, sha1(challenge))
            if (remaining <= 0) {
                break
            } else if (this.requestCache.has(prefix, number)) {
                continue
            }

            remaining -= 1
            this.requestCache.add(PendingChallengesCache(this, sha1(challenge), cache, cache.idFormat))

            val payload = ChallengePayload(attestationHash, challenge)
            val packet = serializePacket(CHALLENGE, payload, prefix = this.prefix)
            this.endpoint.send(peer.address, packet)
        }

    }


    private fun onChallenge(peer: Peer, payload: ChallengePayload) {
        if (!this.attestationKeys.containsKey(ByteArrayKey(payload.attestationHash))) {
            logger.error("Received ChallengePayload $payload for unknown attestation hash ${payload.attestationHash}.")
            return
        }

        val (privateKey, idFormat) = this.attestationKeys[ByteArrayKey(payload.attestationHash)]!!
        val challengeHash = sha1(payload.challenge)
        val algorithm = this.getIdAlgorithm(idFormat)
        val attestation = this.cachedAttestationBlobs[ByteArrayKey(payload.attestationHash)]!!

        val outGoingPayload = ChallengeResponsePayload(challengeHash,
            algorithm.createChallengeResponse(privateKey, attestation, payload.challenge))
        val packet = serializePacket(CHALLENGE_RESPONSE, outGoingPayload, prefix = this.prefix)
        this.endpoint.send(peer.address, packet)

    }

    private fun onChallengeResponse(
        peer: Peer,
        payload: ChallengeResponsePayload,
    ) {
        synchronized(receiveBlockLock) {
            val (prefix, number) = HashCache.idFromHash(PENDING_CHALLENGES_PREFIX, payload.challengeHash)
            if (this.requestCache.has(prefix, number)) {
                val cache = this.requestCache.pop(prefix, number) as PendingChallengesCache
                val provingCache = cache.provingCache
                val (provingCachePrefix, provingCacheId) = HashCache.idFromHash(PROVING_ATTESTATION_PREFIX,
                    provingCache.cacheHash)
                var challenge: ByteArray? = null

                // TODO: Come up with more elegant solution for ByteArray checking.
                val hashElement = provingCache.hashedChallenges.find { x -> x.contentEquals(payload.challengeHash) }
                if (hashElement != null) {
                    provingCache.hashedChallenges.remove(hashElement)
                    for (c in provingCache.challenges) {
                        if (sha1(c).contentEquals(payload.challengeHash)) {
                            provingCache.challenges.remove(provingCache.challenges.first { x -> x.contentEquals(c) })
                            challenge = c
                            break
                        }
                    }
                }
                val algorithm = this.getIdAlgorithm(provingCache.idFormat)
                if (cache.honestyCheck < 0) {
                    algorithm.processChallengeResponse(provingCache.relativityMap, challenge, payload.response)
                } else if (!algorithm.processHonestyChallenge(cache.honestyCheck, payload.response)) {
                    logger.error("${peer.address} attempted to cheat in the ZKP!")
                    if (this.requestCache.has(provingCachePrefix, provingCacheId)) {
                        this.requestCache.pop(provingCachePrefix, provingCacheId)
                    }
                    provingCache.attestationCallbacks(provingCache.cacheHash, algorithm.createCertaintyAggregate(null))
                }

                if (provingCache.hashedChallenges.isEmpty()) {
                    logger.info("Completed attestation verification")
                    // TODO: We can most likely directly call pop.
                    if (this.requestCache.has(provingCachePrefix, provingCacheId)) {
                        this.requestCache.pop(provingCachePrefix, provingCacheId)
                    }
                    provingCache.attestationCallbacks(provingCache.cacheHash, provingCache.relativityMap)
                } else {

                    // TODO: Add secure random.
                    val honestyCheck = algorithm.honestCheck && (Random.nextUBytes(1)[0].toInt() < 38)
                    var honestyCheckByte = if (honestyCheck) arrayOf(0, 1, 2).random() else -1
                    challenge = null
                    if (honestyCheck) {
                        while (challenge == null || this.requestCache.has(HashCache.idFromHash(PENDING_CHALLENGES_PREFIX,
                                sha1(challenge)))
                        ) {
                            challenge = algorithm.createHonestyChallenge(provingCache.publicKey!!, honestyCheckByte)
                        }
                    }
                    if (!honestyCheck || (challenge != null && this.requestCache.has(HashCache.idFromHash(
                            PENDING_CHALLENGES_PREFIX,
                            sha1(challenge))))
                    ) {
                        honestyCheckByte = -1
                        challenge = null
                        for (c in provingCache.challenges) {
                            if (!this.requestCache.has(HashCache.idFromHash(PENDING_CHALLENGES_PREFIX,
                                    sha1(c)))
                            ) {
                                challenge = c
                                break
                            }
                        }
                        if (challenge == null) {
                            logger.info("No more bitpairs to challenge!")
                            return
                        }
                    }
                    logger.info("Sending challenge $honestyCheckByte (${provingCache.hashedChallenges.size}).")
                    this.requestCache.add(PendingChallengesCache(this,
                        sha1(challenge!!),
                        provingCache,
                        cache.idFormat,
                        honestyCheckByte))

                    val outGoingPayload = ChallengePayload(provingCache.cacheHash, challenge)
                    val packet = serializePacket(CHALLENGE, outGoingPayload, prefix = this.prefix)
                    this.endpoint.send(peer.address, packet)
                }

            }
        }

    }

    object MessageId {
        const val VERIFY_ATTESTATION_REQUEST = 1
        const val ATTESTATION = 2
        const val CHALLENGE = 3
        const val CHALLENGE_RESPONSE = 4
        const val ATTESTATION_REQUEST = 5;
    }

    class Factory(
        private val database: AttestationStore,
    ) : Overlay.Factory<AttestationCommunity>(AttestationCommunity::class.java) {
        override fun create(): AttestationCommunity {
            return AttestationCommunity(database)
        }
    }

}


