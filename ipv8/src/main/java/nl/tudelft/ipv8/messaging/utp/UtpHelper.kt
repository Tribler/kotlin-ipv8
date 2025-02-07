package nl.tudelft.ipv8.messaging.utp

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
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
import nl.tudelft.ipv8.messaging.payload.TransferRequestPayload.TransferType
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
    private lateinit var heartbeatJob : Job

    fun startHeartbeat() {
        heartbeatJob = scope.launch(Dispatchers.IO) {
            while (isActive) {
                if (utpCommunity.endpoint.udpEndpoint?.utpIPv8Endpoint?.isOpen() == true) {
                    utpCommunity.sendHeartbeat()
                    delay(5000)
                } else {
                    this.cancel()
                }
            }
        }
    }

    fun stopHeartbeat() {
        heartbeatJob.cancel()
    }

    fun sendFileData(peer: Peer, metadata: NamedResource, data: ByteArray) {
        sendData(peer, metadata, data, TransferType.FILE)
    }

    fun sendRandomData(peer: Peer, size: Int = UtpIPv8Endpoint.getBufferSize()) {
        sendData(peer, NamedResource("random.tmp", 0, size), generateRandomDataBuffer(size), TransferType.RANDOM_DATA)
    }

    private fun sendData(peer: Peer, metadata: NamedResource, data: ByteArray, type: TransferType) {
        scope.launch(Dispatchers.IO) {
            utpCommunity.sendTransferRequest(
                peer,
                metadata.name,
                metadata.size,
                type
            )
            if (!waitForTransferResponse(peer)) return@launch
            println("Sending data to $peer")
            utpCommunity.endpoint.udpEndpoint?.sendUtp(
                IPv4Address(
                    peer.address.ip,
                    peer.address.port
                ), data
            )
        }
    }

    private suspend fun waitForTransferResponse(peer: Peer): Boolean {
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
            return false
        }
        if (payload.status == TransferRequestPayload.TransferStatus.DECLINE) {
            println("Peer $peer declined transfer")
            return false
        }
        return true
    }

    data class NamedResource(
        val name: String,
        val id: Int,
        val size: Int = 0,
    ) {
        override fun toString() = name
    }

    companion object {
        fun generateRandomDataBuffer(size: Int = UtpIPv8Endpoint.getBufferSize()): ByteArray {
            if (size < 32) {
                throw IllegalArgumentException("Buffer size must be at least 32 bytes")
            } else if (size > UtpIPv8Endpoint.getBufferSize()) {
                throw IllegalArgumentException("Buffer size must be at most ${UtpIPv8Endpoint.getBufferSize()} bytes")
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
