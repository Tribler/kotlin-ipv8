package nl.tudelft.ipv8.messaging.utp

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.yield
import nl.tudelft.ipv8.IPv4Address
import nl.tudelft.ipv8.Peer
import nl.tudelft.ipv8.messaging.payload.TransferRequestPayload
import java.nio.ByteBuffer
import java.security.MessageDigest
import kotlin.random.Random

class UtpHelper(
    private val utpCommunity: UtpCommunity
) {

    /**
     * Scope used for network operations
     */
    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.IO + job)

    init {
        scope.launch(Dispatchers.IO) {
            while (isActive) {
                utpCommunity.sendHeartbeat()
                delay(5000)
                if (utpCommunity.endpoint.udpEndpoint?.utpIPv8Endpoint?.isOpen() == false) this.cancel()
            }
        }
    }

    fun sendFileData(peer: Peer, metadata: NamedResource, data: ByteArray) {
        scope.launch(Dispatchers.IO) {
            utpCommunity.sendTransferRequest(
                peer,
                metadata.name,
                metadata.size,
                TransferRequestPayload.TransferType.FILE
            )
            waitForTransferResponse(peer)
            println("Sending data to $peer")
            utpCommunity.endpoint.udpEndpoint?.sendUtp(
                IPv4Address(
                    peer.address.ip,
                    peer.address.port
                ), data
            )
        }
    }

    fun sendRandomData(peer: Peer, size: Int = UtpIPv8Endpoint.BUFFER_SIZE) {
        scope.launch(Dispatchers.IO) {
            utpCommunity.sendTransferRequest(
                peer,
                "random.tmp",
                size,
                TransferRequestPayload.TransferType.RANDOM_DATA
            )
            waitForTransferResponse(peer)
            println("Sending data to $peer")
            utpCommunity.endpoint.udpEndpoint?.sendUtp(
                IPv4Address(
                    peer.address.ip,
                    peer.address.port
                ), generateRandomDataBuffer(size)
            )
        }
    }

    private suspend fun waitForTransferResponse(peer: Peer) {
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
            return
        }
        if (payload.status == TransferRequestPayload.TransferStatus.DECLINE) {
            println("Peer $peer declined transfer")
            return
        }
    }

    data class NamedResource(
        val name: String,
        val id: Int,
        val size: Int = 0,
    ) {
        override fun toString() = name
    }

    companion object {
        fun generateRandomDataBuffer(size: Int = UtpIPv8Endpoint.BUFFER_SIZE): ByteArray {
            if (size < 32) {
                throw IllegalArgumentException("Buffer size must be at least 32 bytes")
            } else if (size > UtpIPv8Endpoint.BUFFER_SIZE) {
                throw IllegalArgumentException("Buffer size must be at most ${UtpIPv8Endpoint.BUFFER_SIZE} bytes")
            }
            val rngByteArray = ByteArray(size)
            Random.nextBytes(rngByteArray, 0, size - 32)
            val buffer = ByteBuffer.wrap(rngByteArray)
            val hash = MessageDigest.getInstance("SHA-256").digest(rngByteArray)
            buffer.position(size - 32)
            buffer.put(hash)
            return buffer.array()
        }
    }
}
