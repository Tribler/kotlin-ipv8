package nl.tudelft.ipv8

import kotlinx.coroutines.*
import mu.KotlinLogging
import nl.tudelft.ipv8.exception.PacketDecodingException
import nl.tudelft.ipv8.keyvault.PrivateKey
import nl.tudelft.ipv8.messaging.Address
import nl.tudelft.ipv8.messaging.EndpointAggregator
import nl.tudelft.ipv8.messaging.Packet
import nl.tudelft.ipv8.messaging.Serializable
import nl.tudelft.ipv8.messaging.eva.*
import nl.tudelft.ipv8.messaging.payload.*
import nl.tudelft.ipv8.peerdiscovery.Network
import nl.tudelft.ipv8.peerdiscovery.WanEstimationLog
import nl.tudelft.ipv8.util.addressIsLan
import nl.tudelft.ipv8.util.hexToBytes
import java.util.*
import kotlin.random.Random

private val logger = KotlinLogging.logger {}

abstract class Community : Overlay {
    protected val prefix: ByteArray
        get() = ByteArray(0) + PREFIX_IPV8 + VERSION + serviceId.hexToBytes()

    override val myEstimatedWan: IPv4Address
        get() {
            return network.wanLog.estimateWan() ?: IPv4Address.EMPTY
        }

    override var myEstimatedLan: IPv4Address = IPv4Address.EMPTY

    private var lastBootstrap: Date? = null

    val messageHandlers = mutableMapOf<Int, (Packet) -> Unit>()

    override lateinit var myPeer: Peer
    override lateinit var endpoint: EndpointAggregator
    override lateinit var network: Network
    override var maxPeers: Int = 20

    private lateinit var job: Job
    protected lateinit var scope: CoroutineScope

    var evaProtocolEnabled = false
    var evaProtocol: EVAProtocol? = null

    init {
        messageHandlers[MessageId.PUNCTURE_REQUEST] = ::onPunctureRequestPacket
        messageHandlers[MessageId.PUNCTURE] = ::onPuncturePacket
        messageHandlers[MessageId.INTRODUCTION_REQUEST] = ::onIntroductionRequestPacket
        messageHandlers[MessageId.INTRODUCTION_RESPONSE] = ::onIntroductionResponsePacket
        messageHandlers[MessageId.EVA_WRITE_REQUEST] = ::onEVAWriteRequestPacket
        messageHandlers[MessageId.EVA_ACKNOWLEDGEMENT] = ::onEVAAcknowledgementPacket
        messageHandlers[MessageId.EVA_DATA] = ::onEVADataPacket
        messageHandlers[MessageId.EVA_ERROR] = ::onEVAErrorPacket
    }

    override fun load() {
        super.load()

        logger.info { "Loading " + javaClass.simpleName + " for peer " + myPeer.mid }

        require(serviceId.length == 2 * Packet.SERVICE_ID_SIZE) {
            "Service ID must be 20 bytes, was $serviceId"
        }

        network.registerServiceProvider(serviceId, this)
        network.blacklistMids.add(myPeer.mid)
        network.blacklist.addAll(DEFAULT_ADDRESSES)

        job = SupervisorJob()
        scope = CoroutineScope(Dispatchers.Main + job)

        if (evaProtocolEnabled)
            evaProtocol = EVAProtocol(this, scope)
    }

    override fun unload() {
        super.unload()

        job.cancel()
    }

    override fun bootstrap() {
        if (endpoint.udpEndpoint == null) return
        if (Date().time - (lastBootstrap?.time ?: 0L) < BOOTSTRAP_TIMEOUT_MS) return
        lastBootstrap = Date()

        for (socketAddress in DEFAULT_ADDRESSES) {
            walkTo(socketAddress)
        }
    }

    override fun walkTo(address: IPv4Address) {
        val packet = createIntroductionRequest(address)
        send(address, packet)
    }

    /**
     * Get a new introduction, or bootstrap if there are no available peers.
     */
    override fun getNewIntroduction(fromPeer: Peer?) {
        var address = fromPeer?.address

        if (address == null) {
            val available = getPeers()
            address = if (available.isNotEmpty()) {
                // With a small chance, try to remedy any disconnected network phenomena.
                if (Random.nextFloat() < 0.5f && endpoint.udpEndpoint != null) {
                    DEFAULT_ADDRESSES.random()
                } else {
                    available.random().address
                }
            } else {
                bootstrap()
                return
            }
        }

        val packet = createIntroductionRequest(address)
        send(address, packet)
    }

    override fun getPeerForIntroduction(exclude: Peer?): Peer? {
        val available = getPeers() - exclude
        return if (available.isNotEmpty()) {
            available.random()
        } else {
            null
        }
    }

    override fun getWalkableAddresses(): List<IPv4Address> {
        return network.getWalkableAddresses(serviceId)
    }

    override fun getPeers(): List<Peer> {
        return network.getPeersForService(serviceId)
    }

    override fun onPacket(packet: Packet) {
        val sourceAddress = packet.source
        val data = packet.data

        val probablePeer = network.getVerifiedByAddress(sourceAddress)
        if (probablePeer != null) {
            probablePeer.lastResponse = Date()
        }

        val packetPrefix = data.copyOfRange(0, prefix.size)
        if (!packetPrefix.contentEquals(prefix)) {
            // logger.debug("prefix not matching")
            return
        }

        val msgId = data[prefix.size].toUByte().toInt()

        val handler = messageHandlers[msgId]

        if (handler != null) {
            try {
                handler(packet)
            } catch (e: Exception) {
                when (e) {
                    is PacketDecodingException -> logger.warn("Unable to decode authenticated payload: $e")
                    else -> e.printStackTrace()
                }
            }
        } else {
            logger.debug("Received unknown message $msgId from $sourceAddress")
        }
    }

    override fun onEstimatedLanChanged(address: IPv4Address) {
        if (myEstimatedLan != address) {
            myEstimatedLan = address
            network.wanLog.clear()
        }
    }

    /**
     * Introduction and puncturing requests creation
     */
    internal fun createIntroductionRequest(
        socketAddress: IPv4Address,
        extraBytes: ByteArray = byteArrayOf()
    ): ByteArray {
        val globalTime = claimGlobalTime()
        val payload = IntroductionRequestPayload(
            socketAddress,
            myEstimatedLan,
            myEstimatedWan,
            true,
            network.wanLog.estimateConnectionType(),
            (globalTime % UShort.MAX_VALUE).toInt(),
            extraBytes
        )

        logger.debug("-> $payload")

        return serializePacket(MessageId.INTRODUCTION_REQUEST, payload)
    }

    fun createIntroductionResponse(
        requester: Peer,
        identifier: Int,
        introduction: Peer? = null,
        prefix: ByteArray = this.prefix,
        extraBytes: ByteArray = byteArrayOf()
    ): ByteArray {
        val intro = introduction ?: getPeerForIntroduction(exclude = requester)
        val introductionLan = intro?.lanAddress ?: IPv4Address.EMPTY
        val introductionWan = intro?.wanAddress ?: IPv4Address.EMPTY

        val payload = IntroductionResponsePayload(
            requester.address,
            myEstimatedLan,
            myEstimatedWan,
            introductionLan,
            introductionWan,
            network.wanLog.estimateConnectionType(),
            false,
            identifier,
            extraBytes
        )

        if (intro != null) {
            // TODO: Seems like a bad practice to send a packet in the create method...
            val packet = createPunctureRequest(
                requester.lanAddress, requester.wanAddress,
                identifier
            )
            send(intro, packet)
        }

        logger.debug("-> $payload")

        return serializePacket(MessageId.INTRODUCTION_RESPONSE, payload, prefix = prefix)
    }

    fun createPuncture(lanWalker: IPv4Address, wanWalker: IPv4Address, identifier: Int): ByteArray {
        val payload = PuncturePayload(lanWalker, wanWalker, identifier)

        logger.debug("-> $payload")

        return serializePacket(MessageId.PUNCTURE, payload)
    }

    internal fun createPunctureRequest(
        lanWalker: IPv4Address,
        wanWalker: IPv4Address,
        identifier: Int
    ): ByteArray {
        logger.debug("-> punctureRequest")
        val payload = PunctureRequestPayload(lanWalker, wanWalker, identifier)
        return serializePacket(MessageId.PUNCTURE_REQUEST, payload, sign = false)
    }

    /**
     * EVA serialized packets for different EVA payloads
     */
    fun createEVAWriteRequest(
        peer: Peer,
        info: String,
        id: String,
        nonce: ULong,
        dataSize: ULong,
        blockCount: UInt,
        blockSize: UInt,
        windowSize: UInt
    ): ByteArray {
        val payload =
            EVAWriteRequestPayload(info, id, nonce, dataSize, blockCount, blockSize, windowSize)
        return serializePacket(MessageId.EVA_WRITE_REQUEST, payload, encrypt = true, recipient = peer)
    }

    fun createEVAAcknowledgement(
        peer: Peer,
        nonce: ULong,
        ackWindow: UInt,
        unReceivedBlocks: ByteArray
    ): ByteArray {
        val payload = EVAAcknowledgementPayload(nonce, ackWindow, unReceivedBlocks)
        return serializePacket(MessageId.EVA_ACKNOWLEDGEMENT, payload, encrypt = true, recipient = peer)
    }

    fun createEVAData(peer: Peer, blockNumber: UInt, nonce: ULong, data: ByteArray): ByteArray {
        val payload = EVADataPayload(blockNumber, nonce, data)
        return serializePacket(MessageId.EVA_DATA, payload, encrypt = true, recipient = peer)
    }

    fun createEVAError(peer: Peer, info: String, message: String): ByteArray {
        val payload = EVAErrorPayload(info, message)
        return serializePacket(MessageId.EVA_ERROR, payload, encrypt = true, recipient = peer)
    }

    /**
     * Serializes a payload into a binary packet that can be sent over the transport.
     *
     * @param messageId The message type ID
     * @param payload The serializable payload
     * @param sign True if the packet should be signed
     * @param peer The peer that should sign the packet. The community's [myPeer] is used by default.
     */
    fun serializePacket(
        messageId: Int,
        payload: Serializable,
        sign: Boolean = true,
        peer: Peer = myPeer,
        prefix: ByteArray = this.prefix,
        encrypt: Boolean = false,
        timestamp: ULong? = null,
        recipient: Peer? = null
    ): ByteArray {
        val payloads = mutableListOf<Serializable>()
        if (sign) {
            payloads += BinMemberAuthenticationPayload(peer.publicKey.keyToBin())
        }
        payloads += GlobalTimeDistributionPayload(timestamp ?: claimGlobalTime())
        payloads += payload
        return serializePacket(
            messageId,
            payloads,
            sign,
            peer,
            prefix,
            encrypt,
            recipient
        )
    }

    /**
     * Serializes multiple payloads into a binary packet that can be sent over the transport.
     *
     * @param messageId The message type ID
     * @param payload The list of payloads
     * @param sign True if the packet should be signed
     * @param peer The peer that should sign the packet. The community's [myPeer] is used by default.
     */
    private fun serializePacket(
        messageId: Int,
        payload: List<Serializable>,
        sign: Boolean = true,
        peer: Peer = myPeer,
        prefix: ByteArray = this.prefix,
        encrypt: Boolean = false,
        recipient: Peer? = null
    ): ByteArray {
        var packet = prefix
        @Suppress("DEPRECATION")
        packet += messageId.toChar().toByte()

        if (encrypt && recipient == null) {
            throw IllegalArgumentException("Recipient must be provided for encryption")
        }

        for ((index, item) in payload.withIndex()) {
            val serialized = item.serialize()
            // Encrypt the main payload if we can
            packet += if (index == payload.size - 1 && encrypt && recipient != null) {
                val encrypted = recipient.publicKey.encrypt(serialized)
                encrypted
            } else {
                serialized
            }
        }

        val myPeerKey = peer.key
        if (sign && myPeerKey is PrivateKey) {
            packet += myPeerKey.sign(packet)
        }

        return packet
    }

    /**
     * Request deserialization
     */
    internal fun onIntroductionRequestPacket(packet: Packet) {
        val (peer, payload) =
            packet.getAuthPayload(IntroductionRequestPayload.Deserializer)
        onIntroductionRequest(peer, payload)
    }

    internal fun onIntroductionResponsePacket(packet: Packet) {
        val (peer, payload) =
            packet.getAuthPayload(IntroductionResponsePayload.Deserializer)
        onIntroductionResponse(peer, payload)
    }

    internal fun onPuncturePacket(packet: Packet) {
        val (peer, payload) = packet.getAuthPayload(PuncturePayload.Deserializer)
        onPuncture(peer, payload)
    }

    internal fun onPunctureRequestPacket(packet: Packet) {
        val payload = packet.getPayload(PunctureRequestPayload.Deserializer)
        if (packet.source is IPv4Address) {
            onPunctureRequest(packet.source, payload)
        }
    }

    /**
     * EVA protocol on receive packet handlers
     *
     * @param packet specific packets for the EVA protocol (write request, ack, data, error)
     */
    internal fun onEVAWriteRequestPacket(packet: Packet) {
        val (peer, payload) = packet.getDecryptedAuthPayload(
            EVAWriteRequestPayload.Deserializer,
            myPeer.key as PrivateKey
        )

        onEVAWriteRequest(peer, payload)
    }

    internal fun onEVAAcknowledgementPacket(packet: Packet) {
        val (peer, payload) = packet.getDecryptedAuthPayload(
            EVAAcknowledgementPayload.Deserializer,
            myPeer.key as PrivateKey
        )

        onEVAAcknowledgement(peer, payload)
    }

    internal fun onEVADataPacket(packet: Packet) {
        val (peer, payload) = packet.getDecryptedAuthPayload(
            EVADataPayload.Deserializer,
            myPeer.key as PrivateKey
        )

        onEVAData(peer, payload)
    }

    internal fun onEVAErrorPacket(packet: Packet) {
        val (peer, payload) = packet.getDecryptedAuthPayload(
            EVAErrorPayload.Deserializer,
            myPeer.key as PrivateKey
        )

        onEVAError(peer, payload)
    }

    /**
     * Request handling
     */
    internal open fun onIntroductionRequest(
        peer: Peer,
        payload: IntroductionRequestPayload
    ) {
        logger.debug("<- $payload")

        if (maxPeers >= 0 && getPeers().size >= maxPeers) {
            logger.info("Dropping introduction request from $peer, too many peers!")
            return
        }

        // Add the sender as a verified peer
        val newPeer = peer.copy(
            lanAddress = payload.sourceLanAddress,
            wanAddress = payload.sourceWanAddress
        )
        addVerifiedPeer(newPeer)

        val packet = createIntroductionResponse(
            newPeer,
            payload.identifier
        )

        send(peer, packet)
    }

    open fun onIntroductionResponse(
        peer: Peer,
        payload: IntroductionResponsePayload
    ) {
        logger.debug("<- $payload")

        addEstimatedWan(peer, payload.destinationAddress)

        // Add the sender as a verified peer
        val newPeer = peer.copy(
            lanAddress = payload.sourceLanAddress,
            wanAddress = payload.sourceWanAddress
        )
        addVerifiedPeer(newPeer)

        // Process introduced addresses
        if (!payload.wanIntroductionAddress.isEmpty() &&
            payload.wanIntroductionAddress.ip != myEstimatedWan.ip
        ) {
            // WAN is not empty and it is not same as ours

            if (!payload.lanIntroductionAddress.isEmpty()) {
                // If LAN address is not empty, add them in case they are on our LAN,
                // even though that should not happen as WAN is different than ours
                discoverAddress(peer, payload.lanIntroductionAddress, serviceId)
            }

            // Discover WAN address. We should contact it ASAP as it just received a puncture
            // request and probably already sent a puncture to us.
            discoverAddress(peer, payload.wanIntroductionAddress, serviceId)
        } else if (!payload.lanIntroductionAddress.isEmpty() &&
            payload.wanIntroductionAddress.ip == myEstimatedWan.ip
        ) {
            // LAN is not empty and WAN is the same as ours => they are on the same LAN
            discoverAddress(peer, payload.lanIntroductionAddress, serviceId)
        } else if (!payload.wanIntroductionAddress.isEmpty()) {
            // WAN is same as ours, but we do not know the LAN
            // Should not happen with kotlin-ipv8 peers, but we'll keep it for py-ipv8 compatibility

            // Try to connect via WAN, NAT needs to support hairpinning
            discoverAddress(peer, payload.wanIntroductionAddress, serviceId)

            // Assume LAN is same as ours (e.g. multiple instances running on a local machine),
            // and port same as for WAN (works only if NAT does not change port)
            discoverAddress(
                peer, IPv4Address(myEstimatedLan.ip, payload.wanIntroductionAddress.port),
                serviceId
            )
        }
    }

    private fun addEstimatedWan(peer: Peer, wan: IPv4Address) {
        // Change our estimated WAN address if the sender is not on the same LAN, otherwise it
        // would just send us our LAN address
        if (!addressIsLan(peer.address) && !peer.address.isLoopback() && !peer.address.isEmpty()) {
            // If this is a new peer, add our estimated WAN to the WAN estimation log which can be
            // used to determine symmetric NAT behavior
            network.wanLog.addItem(
                WanEstimationLog.WanLogItem(
                    Date(), peer.address, myEstimatedLan, wan
                )
            )
        }
    }

    protected fun addVerifiedPeer(peer: Peer) {
        network.addVerifiedPeer(peer)
        network.discoverServices(peer, listOf(serviceId))
    }

    protected open fun discoverAddress(peer: Peer, address: IPv4Address, serviceId: String) {
        // Prevent discovering its own address
        if (address != myEstimatedLan && address != myEstimatedWan && !address.isEmpty()) {
            network.discoverAddress(peer, address, serviceId)
        }
    }

    internal open fun onPuncture(
        peer: Peer,
        payload: PuncturePayload
    ) {
        logger.debug("<- $payload")
        // NOOP
    }

    internal open fun onPunctureRequest(
        address: IPv4Address,
        payload: PunctureRequestPayload
    ) {
        logger.debug("<- $payload")

        val target = if (payload.wanWalkerAddress.ip == myEstimatedWan.ip) {
            // They are on the same LAN, puncture should not be needed, but send it just in case
            payload.lanWalkerAddress
        } else {
            payload.wanWalkerAddress
        }

        val packet = createPuncture(myEstimatedLan, myEstimatedWan, payload.identifier)
        send(target, packet)
    }

    protected fun send(peer: Peer, data: ByteArray) {
        val verifiedPeer = network.getVerifiedByPublicKeyBin(peer.publicKey.keyToBin())
        endpoint.send(verifiedPeer ?: peer, data)
    }

    protected fun send(address: Address, data: ByteArray) {
        val probablePeer = network.getVerifiedByAddress(address)
        if (probablePeer != null) {
            probablePeer.lastRequest = Date()
        }
        endpoint.send(address, data)
    }

    /**
     * EVA protocol on receive handlers
     */
    fun onEVAWriteRequest(peer: Peer, payload: EVAWriteRequestPayload) {
        logger.debug { "ON EVA write request: $payload" }

        evaProtocol?.onWriteRequest(peer, payload)
    }

    fun onEVAAcknowledgement(peer: Peer, payload: EVAAcknowledgementPayload) {
        logger.debug { "ON EVA acknowledgement: $payload" }
        evaProtocol?.onAcknowledgement(peer, payload)
    }

    fun onEVAData(peer: Peer, payload: EVADataPayload) {
        logger.debug { "ON EVA acknowledgement: Block ${payload.blockNumber} with nonce ${payload.nonce}." }
        evaProtocol?.onData(peer, payload)
    }

    fun onEVAError(peer: Peer, payload: EVAErrorPayload) {
        logger.debug { "ON EVA error: $payload" }
        evaProtocol?.onError(peer, payload)
    }

    /**
     * EVA protocol entrypoint to send binary data
     *
     * @param peer the address to deliver the data
     * @param info string that identifies to which communitu or class it should be delivered
     * @param id file/data identifier that identifies the sent data
     * @param data serialized packet in bytes
     * @param nonce an optional unique number that identifies this transfer
     */
    fun evaSendBinary(
        peer: Peer,
        info: String,
        id: String,
        data: ByteArray,
        nonce: Long? = null
    ) = evaProtocol?.sendBinary(peer, info, id, data, nonce)

    /**
     * EVA protocol callbacks for other communities and classes
     */
    fun setOnEVASendCompleteCallback(callback: (peer: Peer, info: String, nonce: ULong) -> Unit) {
        evaProtocol?.onSendCompleteCallback = callback
    }

    fun setOnEVAReceiveProgressCallback(callback: (peer: Peer, info: String, progress: TransferProgress) -> Unit) {
        evaProtocol?.onReceiveProgressCallback = callback
    }

    fun setOnEVAReceiveCompleteCallback(callback: (peer: Peer, info: String, id: String, data: ByteArray?) -> Unit) {
        evaProtocol?.onReceiveCompleteCallback = callback
    }

    fun setOnEVAErrorCallback(callback: (peer: Peer, exception: TransferException) -> Unit) {
        evaProtocol?.onErrorCallback = callback
    }

    companion object {
        val DEFAULT_ADDRESSES: List<IPv4Address> = listOf(
            // Dispersy
            // Address("130.161.119.206", 6421),
            // Address("130.161.119.206", 6422),
            // Address("131.180.27.155", 6423),
            // Address("131.180.27.156", 6424),
            // Address("131.180.27.161", 6427),
            // IPv8
            // Address("131.180.27.161", 6521),
            // Address("131.180.27.161", 6522),
            // Address("131.180.27.162", 6523),
            // Address("131.180.27.162", 6524),
            // Address("130.161.119.215", 6525),
            // Address("130.161.119.215", 6526),
            // Address("81.171.27.194", 6527),
            // Address("81.171.27.194", 6528)
            // py-ipv8 + LibNaCL
            IPv4Address("131.180.27.161", 6427),
            // kotlin-ipv8
            IPv4Address("131.180.27.188", 1337),
            IPv4Address("131.180.27.187", 1337)
        )

        // Timeout before we bootstrap again (bootstrap kills performance)
        private const val BOOTSTRAP_TIMEOUT_MS = 5_000
        const val DEFAULT_MAX_PEERS = 30

        const val PREFIX_IPV8: Byte = 0
        const val VERSION: Byte = 2
    }

    object MessageId {
        const val PUNCTURE_REQUEST = 250
        const val PUNCTURE = 249
        const val INTRODUCTION_REQUEST = 246
        const val INTRODUCTION_RESPONSE = 245
        const val EVA_WRITE_REQUEST = 130
        const val EVA_ACKNOWLEDGEMENT = 131
        const val EVA_DATA = 132
        const val EVA_ERROR = 133
    }

    /**
     * Every community initializes a different version of the EVA protocol (if enabled).
     * To distinguish the incoming packets/requests an ID must be used to hold/let through the
     * EVA related packets.
     */
    object EVAId {
        const val EVA_UNSPECIFIED = ""
        const val EVA_PEERCHAT_ATTACHMENT = "eva_peerchat_attachment"
    }
}
