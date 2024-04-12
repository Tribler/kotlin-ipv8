package nl.tudelft.ipv8.messaging.utp

import nl.tudelft.ipv8.Community
import nl.tudelft.ipv8.IPv4Address
import nl.tudelft.ipv8.Peer
import nl.tudelft.ipv8.exception.PacketDecodingException
import nl.tudelft.ipv8.messaging.Packet
import nl.tudelft.ipv8.messaging.payload.TransferRequestPayload
import nl.tudelft.ipv8.messaging.payload.TransferRequestPayload.TransferStatus
import nl.tudelft.ipv8.messaging.payload.TransferRequestPayload.TransferType
import nl.tudelft.ipv8.messaging.payload.UtpHeartbeatPayload
import java.util.Date

/**
 * A community for the UTP protocol.
 */
class UtpCommunity : Community() {
    override val serviceId = "450ded7389134595dadb6b2549f431ad60156931"

    val lastHeartbeat = mutableMapOf<String, Date>()
    val transferRequests = mutableMapOf<String, TransferRequestPayload>()
    val utpHelper = UtpHelper(this)

    object MessageId {
        const val UTP_HEARTBEAT = 1
        const val UTP_TRANSFER_REQUEST = 2
    }
    init {
        messageHandlers[MessageId.UTP_HEARTBEAT] = ::onHeartbeat
        messageHandlers[MessageId.UTP_TRANSFER_REQUEST] = ::onTransferRequest
    }

    fun sendHeartbeat() {
        val payload = UtpHeartbeatPayload()
        val packet = serializePacket(MessageId.UTP_HEARTBEAT, payload)

        for (peer in getPeers()) {
            endpoint.send(peer.address, packet)
        }
    }

    private fun onHeartbeat(p: Packet) {
        val peer = getPeers().find { it.address == p.source }
        val payload = p.getPayload(UtpHeartbeatPayload.Deserializer)

        if (peer != null) {
            lastHeartbeat[peer.mid] = Date()
        }

        println("Received heartbeat from $peer: $payload")
    }

    fun sendTransferRequest(peer: Peer, filename: String, dataSize: Int, dataType: TransferType) {
        val payload = TransferRequestPayload(filename, TransferStatus.REQUEST, dataType, dataSize)
        val packet = serializePacket(MessageId.UTP_TRANSFER_REQUEST, payload)

        endpoint.send(peer, packet)
    }

    fun sendTransferResponse(peer: Peer, payload: TransferRequestPayload) {
        val packet = serializePacket(MessageId.UTP_TRANSFER_REQUEST, payload)
        endpoint.send(peer.address, packet)
    }

    /**
     * Allows the user to accept or decline a transfer request, or handle any custom logic.
     */
    private fun onTransferRequest(p: Packet) {
        try {
            val (peer, payload) = p.getAuthPayload(TransferRequestPayload.Deserializer)
            if (payload.status == TransferStatus.REQUEST) {
                // Accept the transfer request by default
                val acceptedPayload = payload.copy(status = TransferStatus.ACCEPT)
                sendTransferResponse(peer, acceptedPayload)
                endpoint.udpEndpoint?.utpIPv8Endpoint!!.permittedTransfers[peer.address] = acceptedPayload
            }
            transferRequests[peer.mid] = payload

            println("Received transfer request from $peer: $payload")
        } catch (e: PacketDecodingException) {
            println("Failed to handle transfer request: ${e.message}")
        }
    }

}
