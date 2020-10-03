package nl.tudelft.ipv8.exception

class PacketDecodingException(msg: String, cause: Exception? = null) : Exception(msg, cause)

class PacketDecodingExceptionSilent(msg: String, cause: Exception? = null) : Exception(msg, cause) {
    override fun fillInStackTrace(): Throwable {
        return this
    }
}
