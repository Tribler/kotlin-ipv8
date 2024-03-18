package nl.tudelft.ipv8.messaging.utp.listener

import mu.KotlinLogging
import net.utp4j.channels.futures.UtpReadListener
import nl.tudelft.ipv8.messaging.utp.UtpEndpoint.Companion.BUFFER_SIZE
import java.nio.Buffer

private val logger = KotlinLogging.logger {}

class RawResourceListener : UtpReadListener() {

    val queue: ArrayDeque<ByteArray> = ArrayDeque()

    override fun actionAfterReading() {
        if (exception == null && byteBuffer != null) {
            try {
                byteBuffer.flip()

                // Print the received text file data
                val buf = StringBuffer().apply {
                    while (byteBuffer.hasRemaining()) {
                        append(byteBuffer.get().toInt().toChar())
                    }
                    logger.debug("Received data: $this")
                }

                byteBuffer.clear()
                queue.add(buf.toString().toByteArray())
                // Save the received data to a file


            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }



    override fun getThreadName(): String = "RawResourceListenerThread"
}
