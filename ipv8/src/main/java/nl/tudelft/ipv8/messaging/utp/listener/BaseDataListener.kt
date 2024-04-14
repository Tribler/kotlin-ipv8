package nl.tudelft.ipv8.messaging.utp.listener

import mu.KotlinLogging
import net.utp4j.channels.futures.UtpReadListener
import nl.tudelft.ipv8.messaging.utp.UtpIPv8Endpoint.Companion.BUFFER_SIZE
import java.security.MessageDigest

private val logger = KotlinLogging.logger {}

/**
 * A listener for random hashed data.
 * It expects a SHA256 hash at the end of the received data to check for integrity.
 *
 * Used for testing purposes.
 */
class BaseDataListener : TransferListener() {

    override val queue: ArrayDeque<ByteArray> = ArrayDeque()
    override fun actionAfterReading() {
        if (exception == null && byteBuffer != null) {
            try {
                byteBuffer.flip()
                // Unpack received hash
                val receivedHashData = ByteArray(32)
                val dataLength = byteBuffer.remaining()
                val data = ByteArray(dataLength)
                byteBuffer.get(data, 0, dataLength - 32)
                byteBuffer.get(receivedHashData)

                // Hash the received data
                val hash = MessageDigest.getInstance("SHA-256").digest(data)

                if (MessageDigest.isEqual(hash, receivedHashData)) {
                    println("Correct hash received")
                    queue.add(data)
                } else {
                    println("Invalid hash received!")
                    queue.add(ByteArray (32) {_ -> 0x00})
                }

                // Display the received data

            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    override fun getThreadName(): String = "BaseDataListenerThread"
}
