package nl.tudelft.ipv8.messaging.eva

import mu.KotlinLogging
import java.io.*

private val logger = KotlinLogging.logger {}

class EVADebugger(
    private val logPath: String
) {
    init {
        val logFolder = File(logPath)
        if (!logFolder.exists()) logFolder.mkdir()
    }

    private fun initFile(id: String): Boolean {
        val file = File(logPath, "$id.csv")

        if (!file.exists()) {
            return try {
                file.createNewFile()
                logger.debug { "EVADEBUGGER: FILE ${file.path} has been created" }

                true
            } catch (e: IOException) {
                e.printStackTrace()
                logger.debug { "EVADEBUGGER: FILE ${file.path} couldn't be created: ${e.localizedMessage}" }

                false
            }
        }

        return true
    }

    fun write(id: String, type: String, text: String) {
        if (initFile(id)) {
            try {
                val file = File(logPath, "$id.csv")
                FileOutputStream(file, true).bufferedWriter().use { writer ->
                    writer.write("$id|")
                    writer.write("$type|")
                    writer.write(text)
                    writer.newLine()
                }
            } catch(e: IOException) {
                e.printStackTrace()

                logger.debug { "EVADEBUGGER: Writing to file failed: ${e.localizedMessage}" }
            }
        } else {
            logger.debug { "EVADEBUGGER: Init file failed, couldn't write to file" }
        }
    }

    companion object {
        const val TYPE_SEND_WRITE_REQUEST = "SEND_WRITE_REQUEST"
        const val TYPE_WRITE_REQUEST = "ON_WRITE_REQUEST"
        const val TYPE_SEND_ACK = "SEND_ACKNOWLEDGEMENT"
        const val TYPE_ON_ACK = "ON_ACKNOWLEDGEMENT"
        const val TYPE_SEND_DATA = "SEND_DATA"
        const val TYPE_ON_DATA = "ON_DATA"
        const val TYPE_SEND_ERROR = "SEND_ERROR"
        const val TYPE_ON_ERROR = "ON_ERROR"
        const val TYPE_FINISH_INCOMING = "FINISH_INCOMING"
        const val TYPE_FINISH_OUTGOING = "FINISH_OUTGOING"
    }
}
