package nl.tudelft.ipv8.messaging.utp

import nl.tudelft.ipv8.Community
import nl.tudelft.ipv8.Peer
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

    fun onHeartbeat(p: Packet) {
        val peer = getPeers().find { it.address == p.source }
        val payload = p.getPayload(UtpHeartbeatPayload.Deserializer)

        if (peer != null) {
            lastHeartbeat[peer.mid] = Date()
        }

        TODO("Process the heartbeat message $payload")
    }

    fun sendTransferRequest(peer: Peer, filename: String, dataSize: Int, dataType: TransferType) {
        val payload = TransferRequestPayload(filename, TransferStatus.REQUEST, dataType, dataSize)
        val packet = serializePacket(MessageId.UTP_TRANSFER_REQUEST, payload)

        endpoint.send(peer.address, packet)
    }

    fun sendTransferResponse(peer: Peer, payload: TransferRequestPayload) {
        val packet = serializePacket(MessageId.UTP_TRANSFER_REQUEST, payload)

        endpoint.send(peer.address, packet)

    }

    fun onTransferRequest(p: Packet) {
        val peer = getPeers().find { it.address == p.source }
        val payload = p.getPayload(TransferRequestPayload)

        TODO("Not yet implemented $peer, $payload")
    }

}
