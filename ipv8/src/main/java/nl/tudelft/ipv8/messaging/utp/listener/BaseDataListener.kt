package nl.tudelft.ipv8.messaging.utp.listener

import mu.KotlinLogging
import net.utp4j.channels.futures.UtpReadListener
import nl.tudelft.ipv8.messaging.utp.UtpEndpoint.Companion.BUFFER_SIZE
import java.security.MessageDigest

private val logger = KotlinLogging.logger {}

class BaseDataListener : UtpReadListener() {
    override fun actionAfterReading() {
        if (exception == null && byteBuffer != null) {
            try {
                byteBuffer.flip()
                // Unpack received hash
                val receivedHashData = ByteArray(32)
                val data = ByteArray(BUFFER_SIZE + 32)
                byteBuffer.get(data, 0, BUFFER_SIZE)
                byteBuffer.get(receivedHashData)

                // Hash the received data
                val hash = MessageDigest.getInstance("SHA-256").digest(data)

                if (MessageDigest.isEqual(hash, receivedHashData)) {
                    logger.debug("Correct hash received")
                } else {
                    logger.debug("Invalid hash received!")
                }

                // Display the received data

            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    override fun getThreadName(): String = "BaseDataListenerThread"
}
