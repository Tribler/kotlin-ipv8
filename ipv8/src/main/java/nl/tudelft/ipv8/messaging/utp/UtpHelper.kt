package nl.tudelft.ipv8.messaging.utp

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.yield
import nl.tudelft.ipv8.IPv4Address
import nl.tudelft.ipv8.Peer
import nl.tudelft.ipv8.messaging.payload.TransferRequestPayload

class UtpHelper(
    private val utpCommunity: UtpCommunity
) {

    /**
     * Scope used for network operations
     */
    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.IO + job)

    fun sendFileData(peer: Peer, metadata: NamedResource, data: ByteArray) {
        scope.launch(Dispatchers.IO) {
            utpCommunity.sendTransferRequest(
                peer,
                metadata.name,
                metadata.size,
                TransferRequestPayload.TransferType.FILE
            )
            // Wait for response or timeout
            // TODO: This should be handled with a proper pattern instead of a timeout
            println("Waiting for response from $peer")
            val payload = withTimeoutOrNull(5000) {
                while (!utpCommunity.transferRequests.containsKey(peer.mid)) {
                    yield()
                }
                println("Received response from $peer")
                return@withTimeoutOrNull utpCommunity.transferRequests.remove(peer.mid)
            }
            if (payload == null) {
                println("No response from $peer, aborting transfer")
                return@launch
            }
            if (payload.status == TransferRequestPayload.TransferStatus.DECLINE) {
                println("Peer $peer declined transfer")
                return@launch
            }
            println("Sending data to $peer")
            utpCommunity.endpoint.udpEndpoint?.sendUtp(IPv4Address(peer.address.ip, peer.address.port), data)
        }
    }

    data class NamedResource(
        val name: String,
        val id: Int,
        val size: Int = 0,
    ) {
        override fun toString() = name
    }
}
