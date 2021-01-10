package nl.tudelft.ipv8.attestation.wallet

import com.google.gson.Gson
import mu.KotlinLogging
import nl.tudelft.ipv8.Community
import nl.tudelft.ipv8.Peer
import nl.tudelft.ipv8.keyvault.PrivateKey
import nl.tudelft.ipv8.messaging.payload.BinMemberAuthenticationPayload
import nl.tudelft.ipv8.messaging.payload.GlobalTimeDistributionPayload
import java.util.*
import com.google.gson.JsonObject as JsonObject


private val logger = KotlinLogging.logger {}

open class WalletCommunity : Community() {
    override val serviceId = ""
    private val attestationRequestCache: MutableMap<String, AttestationRequest> = mutableMapOf()

    private val attestationKeys: MutableMap<String, Pair<PrivateKey, String>> = mutableMapOf()
    private val cachedAttestationBlobs = mutableMapOf<Any, Any>()
    private val allowedAttestations = mutableMapOf<String, Array<String>>()


    init {

    }


    fun requestAttestation(
        peer: Peer,
        attributeName: String,
        secretKey: PrivateKey,
        metadata: String = ""
    ) {
        val publicKey = secretKey.pub()
        var metadata = Gson().fromJson(metadata, JsonObject::class.java)


        metadata.get("attribute") ?: metadata.addProperty("attribute", attributeName)
        metadata.get("public_key")
            ?: metadata.addProperty("public_key", publicKey.keyToBin().toString())
        metadata.get("id_format") ?: metadata.addProperty("id_format", "id_metadata")
        val metadataString = metadata.toString()

        val globalTime = claimGlobalTime()
//        val auth = BinMemberAuthenticationPayload(myPeer.publicKey.keyToBin())
        val payload = RequestAttestationPayload(metadataString)
//        val dist = GlobalTimeDistributionPayload(globalTime)

        val gtimeStr = globalTime.toString()
        this.attestationRequestCache[peer.mid + gtimeStr] = AttestationRequest(
            this,
            peer.mid + gtimeStr,
            secretKey,
            attributeName,
            metadata.get("id_format").toString()
        )
        this.allowedAttestations[peer.mid] =
            (this.allowedAttestations[peer.mid] ?: emptyArray()) + arrayOf(gtimeStr)

        val packet =
            serializePacket(MessageId.ATTESTATION_REQUEST, payload, true, timestamp = globalTime)
        endpoint.send(peer, packet)
    }



    object MessageId {
        const val ATTESTATION_REQUEST = 5;
    }


    class AttestationRequest(
        community: WalletCommunity,
        mid: String,
        key: PrivateKey,
        name: String,
        id_format: String
    ) {
        val community = community
        val mid = mid
        val key = key
        val name = name
        val id_format = id_format

        fun onTimeOut() {
            logger.info("Attestation Request $mid")
        }
    }


}
