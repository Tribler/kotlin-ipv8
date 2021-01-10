package nl.tudelft.ipv8.attestation.wallet

import mu.KotlinLogging
import nl.tudelft.ipv8.Community
import nl.tudelft.ipv8.Peer
import nl.tudelft.ipv8.keyvault.PrivateKey
import nl.tudelft.ipv8.messaging.payload.GlobalTimeDistributionPayload
import org.json.JSONObject
import java.util.*


private val logger = KotlinLogging.logger {}

open class WalletCommunity : Community() {
    override val serviceId = ""
    private val attestationRequestCache: MutableMap<String, AttestationRequest> = mutableMapOf()

    private val attestationKeys: MutableMap<String, Pair<PrivateKey, String>> = mutableMapOf()
    private val cachedAttestationBlobs = mutableMapOf<Any, Any>()
    private val allowedAttestations = mutableMapOf<String, Array<String>>()


    fun requestAttestation(
        peer: Peer,
        attributeName: String,
        privateKey: PrivateKey,
        metadata: String = "{}"
    ) {
        val publicKey = privateKey.pub()
        val inputMetadata = JSONObject(metadata)
        val idFormat = inputMetadata.opt("id_format")

        val metadataJson = JSONObject()
        metadataJson.put("attribute", attributeName)
        metadataJson.put("public_key", publicKey.keyToBin())
        metadataJson.putOpt("id_format", idFormat)

        inputMetadata.keys().forEach {
            metadataJson.put(it, inputMetadata.get(it))
        }

        val globalTime = claimGlobalTime()
        val payload = RequestAttestationPayload(metadataJson.toString())

        val gTimeStr = globalTime.toString()
        this.attestationRequestCache[peer.mid + gTimeStr] = AttestationRequest(
            this,
            peer.mid + gTimeStr,
            privateKey,
            attributeName,
            metadataJson.get("id_format").toString()
        )
        this.allowedAttestations[peer.mid] =
            (this.allowedAttestations[peer.mid] ?: emptyArray()) + arrayOf(gTimeStr)

        val packet =
            serializePacket(MessageId.ATTESTATION_REQUEST, payload, true, timestamp = globalTime)
        endpoint.send(peer, packet)
    }

    suspend fun onRequestAttestation(
        peer: Peer,
        dist: GlobalTimeDistributionPayload,
        payload: RequestAttestationPayload
    ) {

        val attribute = metadata.get("attribute")

        val pubkeyEncoded = metadata.get("public_key").asString
        val idFormat = metadata.get("id_format")
        val idAlgorithm = TODO() // TODO: add schemas


    }


    object MessageId {
        const val ATTESTATION_REQUEST = 5;
    }


    class AttestationRequest(
        val community: WalletCommunity,
        val mid: String,
        val privateKey: PrivateKey,
        val name: String,
        val idFormat: String
    ) {
        fun onTimeOut() {
            logger.info("Attestation Request $mid")
        }
    }


}
