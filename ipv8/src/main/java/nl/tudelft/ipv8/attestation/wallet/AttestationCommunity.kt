package nl.tudelft.ipv8.attestation.wallet

import mu.KotlinLogging
import nl.tudelft.ipv8.Community
import nl.tudelft.ipv8.IPv4Address
import nl.tudelft.ipv8.Peer
import nl.tudelft.ipv8.attestation.WalletAttestation
import nl.tudelft.ipv8.attestation.IdentityAlgorithm
import nl.tudelft.ipv8.attestation.schema.SchemaManager
import nl.tudelft.ipv8.attestation.wallet.caches.HashCache
import nl.tudelft.ipv8.attestation.wallet.caches.PeerCache
import nl.tudelft.ipv8.attestation.wallet.caches.PendingChallengesCache
import nl.tudelft.ipv8.attestation.wallet.cryptography.bonehexact.BonehPrivateKey
import nl.tudelft.ipv8.attestation.wallet.cryptography.bonehexact.BonehPublicKey
import nl.tudelft.ipv8.attestation.wallet.payloads.*
import nl.tudelft.ipv8.messaging.payload.GlobalTimeDistributionPayload
import nl.tudelft.ipv8.util.hexToBytes
import nl.tudelft.ipv8.util.sha1
import org.json.JSONObject
import java.math.BigInteger
import java.util.*
import java.util.concurrent.locks.ReentrantLock
import kotlin.collections.ArrayList
import kotlin.collections.HashMap
import kotlin.random.Random
import kotlin.random.nextUBytes


private val logger = KotlinLogging.logger {}
private const val CHUNK_SIZE = 800

class AttestationCommunity(private val database: AttestationStore) : Community() {
    override val serviceId = "b42c93d167a0fc4a0843f917d4bf1e9ebb340ec4"

    private val receiveBlockLock = ReentrantLock()
    private val schemaManager = SchemaManager()

    private lateinit var attestationRequestCallback: (peer: Peer, attributeName: String, metaData: String) -> ByteArray
    private lateinit var attestationRequestCompleteCallback: (forPeer: Peer, attributeName: String, attributeHash: ByteArray, idFormat: String, fromPeer: Peer?) -> Unit
    private lateinit var verifyRequestCallback: (attributeName: Peer, attributeHash: ByteArray) -> Boolean

    private val attestationKeys: MutableMap<ByteArray, Pair<BonehPrivateKey, String>> = mutableMapOf()

    private val cachedAttestationBlobs = mutableMapOf<ByteArray, WalletAttestation>()
    private val allowedAttestations = mutableMapOf<String, Array<ByteArray>>()

    private val attestationRequestCache: MutableMap<String, AttestationRequest> = mutableMapOf()
    private val pendingChallengesCache: MutableMap<String, PendingChallenge> = mutableMapOf()
    private val provingAttestationCache: MutableMap<String, ProvingAttestationRequest> = mutableMapOf()
    private val receiveVerifyRequestCache: MutableMap<String, VerifyAttestationRequest> = mutableMapOf()


    init {
        schemaManager.registerDefaultSchemas()
        for (att in this.database.getAll()) {
            val hash = att.attestationHash
            val key = att.key
            val idFormat = att.idFormat
            this.attestationKeys[hash] = Pair(this.getIdAlgorithm(idFormat).loadSecretKey(key), idFormat)
        }

    }

    fun getIdAlgorithm(idFormat: String): IdentityAlgorithm {
        return this.schemaManager.getAlgorithmInstance(idFormat)
    }

    fun setAttestationRequestCallback(f: (peer: Peer, attributeName: String, metaData: String) -> ByteArray) {
        this.attestationRequestCallback = f
    }

    fun setAttestationRequestCompleteCallback(f: (forPeer: Peer, attributeName: String, attributeHash: ByteArray, idFormat: String, fromPeer: Peer?) -> Unit) {
        this.attestationRequestCompleteCallback = f
    }

    fun setVerifyRequestCallback(f: (attributeName: Peer, attributeHash: ByteArray) -> Boolean) {
        this.verifyRequestCallback = f
    }

    fun dumbBlob(attributeName: String, idFormat: String, blob: ByteArray, metaData: String = "") {
        val idAlgorithm = this.getIdAlgorithm(idFormat)
        // TODO: 20/01/2021
        throw NotImplementedError()
    }

    fun requestAttestation(
        peer: Peer,
        attributeName: String,
        privateKey: BonehPrivateKey,
        metadata: String = "{}",
    ) {
        val publicKey = privateKey.publicKey()
        val inputMetadata = JSONObject(metadata)
        val idFormat = inputMetadata.optString("id_format", "id_metadata")

        val metadataJson = JSONObject()
        metadataJson.put("attribute", attributeName)
        // Encode to UTF-8
        metadataJson.put("public_key", String(Base64.getEncoder().encode(publicKey.serialize())))
        metadataJson.putOpt("id_format", idFormat)

        inputMetadata.keys().forEach {
            metadataJson.put(it, inputMetadata.get(it))
        }

        val globalTime = claimGlobalTime()
        val payload = RequestAttestationPayload(metadataJson.toString())

        val gTimeStr = globalTime.toString().toByteArray()
        this.attestationRequestCache[peer.mid + gTimeStr] = AttestationRequest(
            peer.mid + gTimeStr,
            privateKey,
            attributeName,
            metadataJson.get("id_format").toString()
        )
        this.allowedAttestations[peer.mid] =
            (this.allowedAttestations[peer.mid] ?: emptyArray()) + arrayOf(gTimeStr)

        val packet =
            serializePacket(MessageId.ATTESTATION_REQUEST,
                payload,
                prefix = this.prefix,
                timestamp = globalTime)
        endpoint.send(peer, packet)
    }

    suspend fun onRequestAttestation(
        peer: Peer,
        dist: GlobalTimeDistributionPayload,
        payload: RequestAttestationPayload,
    ) {
        val metadata = JSONObject(payload.metadata)
        val attribute = metadata.getString("attribute")
        val pubkeyEncoded = metadata.getString("public_key").toByteArray()
        val idFormat = metadata.getString("id_format")
        val idAlgorithm = this.getIdAlgorithm(idFormat)

        // TODO: maybe_coroutine
        val value = this.attestationRequestCallback(peer, attribute, payload.metadata)

        // Decode as UTF-8 ByteArray
        val publicKey = idAlgorithm.loadPublicKey(Base64.getDecoder().decode(pubkeyEncoded))
        val attestationBlob = idAlgorithm.attest(publicKey, value)
        val attestation =
            idAlgorithm.deserialize(attestationBlob, idFormat)

        this.attestationRequestCompleteCallback(peer,
            attribute,
            attestation.getHash(),
            idFormat,
            null)
        this.sendAttestation(peer.address, attestationBlob, dist.globalTime)
    }

    fun onAttestationComplete(
        deserialized: WalletAttestation,
        privateKey: BonehPrivateKey,
        peer: Peer,
        name: String,
        attestationHash: ByteArray,
        idFormat: String,
    ) {
        this.attestationKeys[attestationHash] = Pair(privateKey, idFormat)
        this.database.insertAttestation(deserialized, attestationHash, privateKey, idFormat)
        this.attestationRequestCompleteCallback(this.myPeer, name, attestationHash, idFormat, peer)
    }

    fun verifyAttestationValues(
        socketAddress: IPv4Address,
        attestationHash: ByteArray,
        values: ArrayList<ByteArray>,
        callback: ((ByteArray, List<Float>) -> Unit),
        idFormat: String,
    ) {
        val algorithm = this.getIdAlgorithm(idFormat)

        val onComplete: ((ByteArray, HashMap<Int, Int>) -> Unit) =
            { attestationHash: ByteArray, relativityMap: HashMap<Int, Int> ->
                callback(attestationHash, values.map { algorithm.certainty(it, relativityMap) })
            }

        this.provingAttestationCache[HashCache.idFromHash("proving-attestation", attestationHash).second.toString()] =
            ProvingAttestationRequest(attestationHash, idFormat, onComplete = onComplete)
        this.createVerifyAttestationRequest(socketAddress, attestationHash, idFormat)
    }

    fun createVerifyAttestationRequest(socketAddress: IPv4Address, attestationHash: ByteArray, idFormat: String) {
        this.receiveVerifyRequestCache[HashCache.idFromHash("receive-verify-attestation",
            attestationHash).second.toString()] =
            VerifyAttestationRequest(attestationHash, idFormat)

        val payload = VerifyAttestationRequestPayload(attestationHash)
        val packet = serializePacket(1, payload, prefix = this.prefix)
        this.endpoint.send(socketAddress, packet)
    }

    fun onVerifyAttestationRequest(
        peer: Peer,
        dist: GlobalTimeDistributionPayload,
        payload: VerifyAttestationRequestPayload,
    ) {
        val attestationBlob = this.database.getAttestationByHash(payload.hash)
        if (attestationBlob == null) {
            logger.warn("Dropping verification request of unknown hash ${payload.hash}!")
            return
        }
        // TODO: Verify whether `attestationBlob` requires to be unpacked.
        if (attestationBlob.isEmpty()) {
            logger.warn("Attestation blob for verification is empty: ${payload.hash}!")
        }

        val value = verifyRequestCallback(peer, payload.hash)
        if (!value) {
            logger.info("Verify request callback returned false for $peer, ${payload.hash}")
            return
        }

        val (privateKey, idFormat) = this.attestationKeys[payload.hash]!!
        val privateAttestation = schemaManager.deserializePrivate(privateKey, attestationBlob, idFormat)
        val publicAttestationBlob = privateAttestation.serialize()
        this.cachedAttestationBlobs[payload.hash] = privateAttestation
        this.sendAttestation(peer.address, publicAttestationBlob)
    }

    fun sendAttestation(sockedAddress: IPv4Address, blob: ByteArray, globalTime: ULong? = null) {
        var globalTime = globalTime
        var sequenceNumber = 0
        // Lastindex as we want to enumerate all indices.
        for (i in 0..blob.lastIndex step CHUNK_SIZE) {
            val blobChunk = blob.copyOfRange(i, i + CHUNK_SIZE)
            logger.debug("Sending attestation chunk $sequenceNumber to $sockedAddress")

            val payload = AttestationChunkPayload(sha1(blob), sequenceNumber, blobChunk)
            val packet = serializePacket(2, payload, prefix = this.prefix, timestamp = globalTime)
            this.endpoint.send(sockedAddress, packet)
            sequenceNumber += 1
        }
    }

    fun onAttestationChunk(peer: Peer, dist: GlobalTimeDistributionPayload, payload: AttestationChunkPayload) {
        val hashId = HashCache.idFromHash("receive-verify-attestation", payload.hash).second.toString()
        val peerIds = arrayListOf<Pair<String, BigInteger>>()
        val allowedGlobs = this.allowedAttestations.get(peer.mid) ?: arrayOf()
        allowedGlobs.forEach {
            if (it.contentEquals(dist.globalTime.toString().toByteArray())) {
                peerIds.add(PeerCache.idFromAddress("receive-request-attestation",
                    peer.mid + it))
            }
        }
        if (this.attestationRequestCache.containsKey(hashId)) {
            val cache = this.attestationRequestCache[hashId]!!
            cache.attestationMap.add(Pair(payload.sequenceNumber, payload.data))

            var serialized = byteArrayOf()
            for ((_, chunk) in cache.attestationMap.sortedBy { attestation -> attestation.first }) {
                serialized += chunk
            }

            if (sha1(serialized).contentEquals(payload.hash)) {
                val deserialized = schemaManager.deserialize(serialized, cache.idFormat)
                this.attestationRequestCache.remove(hashId)
                this.onReceivedAttestation(peer, deserialized, payload.hash)
            }

            logger.debug("Received attestation chunk ${payload.sequenceNumber} for proving by $peer")
        } else {
            var handled = false
            for (peerId in peerIds) {
                if (this.attestationRequestCache.containsKey(peerId.second.toString())) {
                    var cache = this.attestationRequestCache[hashId]
                    cache!!.attestationMap.add(Pair(payload.sequenceNumber, payload.data))

                    var serialized = byteArrayOf()
                    for ((_, chunk) in cache.attestationMap.sortedBy { attestation -> attestation.first }) {
                        serialized += chunk
                    }

                    if (sha1(serialized).contentEquals(payload.hash)) {
                        val deserialized =
                            schemaManager.deserializePrivate(cache.privateKey, serialized, cache.idFormat)
                        cache = this.attestationRequestCache.remove(peerId.second.toString())!!

                        this.allowedAttestations[peer.mid] = this.allowedAttestations[peer.mid]!!.mapNotNull {
                            if (!it.contentEquals(dist.globalTime.toString().toByteArray())) it else null
                        }.toTypedArray()
                        if (this.allowedAttestations[peer.mid].isNullOrEmpty()) {
                            this.allowedAttestations.remove(peer.mid)
                        }

                        this.onAttestationComplete(deserialized,
                            cache.privateKey,
                            peer,
                            cache.name,
                            deserialized.getHash(),
                            cache.idFormat)
                    }

                    logger.debug("Received attestation chunk ${payload.sequenceNumber} for my attribute ${cache.name}")
                    handled = true
                }
            }
            if (!handled) {
                logger.warn("Received Attestation chunk which we did not request!")
            }
        }

    }

    fun onReceivedAttestation(peer: Peer, attestation: WalletAttestation, attestationHash: ByteArray) {
        // TODO remove force
        val algorithm = this.getIdAlgorithm(attestation.idFormat!!)

        val relativityMap = algorithm.createCertaintyAggregate(attestation)
        val hashedChallenges = arrayListOf<ByteArray>()
        val cache =
            this.provingAttestationCache[HashCache.idFromHash("proving-attestation",
                attestationHash).second.toString()]

        if (cache == null) {
            logger.error("Received attestation $attestation from $peer for non-existing cache entry.")
            return
        }

        val challenges = algorithm.createChallenges(attestation.publicKey, attestation)
        for (challenge in challenges) {
            val challengeHash = sha1(challenge)
            hashedChallenges.add(challengeHash)
        }

        cache.relativityMap = relativityMap
        cache.hashedChallenges = hashedChallenges
        cache.challenges = challenges
        logger.debug("Sending ${challenges.size} challenges to $peer.")

        var remaining = 10
        for (challenge in challenges) {
            if (remaining <= 0) {
                break
            } else if (this.pendingChallengesCache.contains(PendingChallengesCache.idFromHash("proving-hash",
                    sha1(challenge)).second.toString())
            ) {
                continue
            }

            remaining -= 1
            // TODO: Fix Caches
            this.pendingChallengesCache[PendingChallengesCache.idFromHash("proving-hash",
                sha1(challenge)).second.toString()] = PendingChallenge(sha1(challenge), cache.idFormat, cache)

            val payload = ChallengePayload(attestationHash, challenge)
            val packet = serializePacket(3, payload, prefix = this.prefix)
            this.endpoint.send(peer.address, packet)
        }

    }


    fun onChallenge(peer: Peer, dist: GlobalTimeDistributionPayload, payload: ChallengePayload) {
        if (!this.attestationKeys.containsKey(payload.attestationHash)) {
            logger.error("Received ChallengePayload $payload for unknown attestation hash ${payload.attestationHash}.")
            return
        }

        val (privateKey, idFormat) = this.attestationKeys[payload.attestationHash]!!
        val challengeHash = sha1(payload.challenge)
        val algorithm = this.getIdAlgorithm(idFormat)
        val attestation = this.cachedAttestationBlobs[payload.attestationHash]!!

        val payload = ChallengeResponsePayload(challengeHash,
            algorithm.createChallengeResponse(privateKey, attestation, payload.challenge))
        val packet = serializePacket(4, payload, prefix = this.prefix)
        this.endpoint.send(peer.address, packet)

    }

    fun onChallengeResponse(peer: Peer, dist: GlobalTimeDistributionPayload, payload: ChallengeResponsePayload) {
        synchronized(receiveBlockLock) {
            val id = HashCache.idFromHash("proving-hash", payload.challengeHash).second.toString()
            if (this.pendingChallengesCache.containsKey(id)) {
                val cache = this.pendingChallengesCache.remove(id)!!
                val provingCache = cache.provingCache
                val (provingCachePrefix, provingCacheId) = HashCache.idFromHash("proving-attestation",
                    provingCache.cacheHash)
                var challenge: ByteArray? = null
                if (payload.challengeHash in provingCache.hashedChallenges) {
                    provingCache.hashedChallenges.remove(payload.challengeHash)
                    for (c in provingCache.challenges) {
                        if (sha1(c) == payload.challengeHash) {
                            provingCache.challenges.remove(c)
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
                    this.provingAttestationCache.remove(provingCacheId.toString())
                    provingCache.onComplete(provingCache.cacheHash, algorithm.createCertaintyAggregate(null))
                }
                // TODO: Verify that null entries not occur.
                if (provingCache.hashedChallenges.isEmpty()) {
                    logger.info("Completed attestation verification")
                    this.provingAttestationCache.remove(provingCacheId.toString())
                    provingCache.onComplete(provingCache.cacheHash, provingCache.relativityMap)
                } else {
                    // TODO: add secure random.
                    val honestyCheck = algorithm.honestCheck and (Random.nextUBytes(1)[0] < 38u)
                    var honestyCheckByte = if (honestyCheck) arrayOf(0, 1, 2).random() else -1
                    challenge = null
                    if (honestyCheck) {
                        while (challenge == null || this.pendingChallengesCache.containsKey(HashCache.idFromHash("proving-hash",
                                sha1(challenge!!)).second.toString())
                        ) {
                            challenge = algorithm.createHonestyChallenge(provingCache.publicKey!!, honestyCheckByte)

                        }
                    }
                    if (!honestyCheck || (challenge != null && this.pendingChallengesCache.containsKey(HashCache.idFromHash(
                            "proving-hash",
                            sha1(challenge)).second.toString()))
                    ) {
                        honestyCheckByte = -1
                        challenge = null
                        for (c in provingCache.challenges) {
                            if (!this.pendingChallengesCache.containsKey(HashCache.idFromHash("proving-hash",
                                    sha1(c)).second.toString())
                            ) {
                                challenge = c
                                break
                            }
                        }
                        if (challenge == null) {
                            logger.debug("No more bitpairs to challenge!")
                            return
                        }
                    }
                    logger.debug("Sending challenge $honestyCheckByte (${provingCache.hashedChallenges.size}).")
                    this.pendingChallengesCache[HashCache.idFromHash("proving-hash",
                        sha1(challenge!!)).second.toString()] =
                        PendingChallenge(sha1(challenge), cache.idFormat, provingCache, honestyCheckByte)

                    val payload = ChallengePayload(provingCache.cacheHash, challenge)
                    val packet = serializePacket(3, payload, prefix = this.prefix)
                    this.endpoint.send(peer.address, packet)
                }

            }
        }

    }


    object MessageId {
        const val ATTESTATION_REQUEST = 5;
    }


    class AttestationRequest(
        val mid: String,
        val privateKey: BonehPrivateKey,
        val name: String,
        val idFormat: String,
        val attestationMap: MutableSet<Pair<Int, ByteArray>> = mutableSetOf(),
    ) {
        fun onTimeOut() {
            logger.info("Attestation Request $mid has timed out.")
        }
    }

    class ProvingAttestationRequest(
        val cacheHash: ByteArray,
        val idFormat: String,
        var relativityMap: HashMap<Int, Int> = hashMapOf(),
        var hashedChallenges: ArrayList<ByteArray> = arrayListOf(),
        var challenges: ArrayList<ByteArray> = arrayListOf(),
        val publicKey: BonehPublicKey? = null,
        val onComplete: (ByteArray, HashMap<Int, Int>) -> Unit = { _: ByteArray, _: HashMap<Int, Int> -> null },
    )

    class PendingChallenge(
        val cacheHash: ByteArray,
        val idFormat: String,
        val provingCache: ProvingAttestationRequest,
        val honestyCheck: Int = -1,
    )

    class VerifyAttestationRequest(val attestationHash: ByteArray, val idFormat: String)

}