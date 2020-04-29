package nl.tudelft.ipv8.messaging.tftp

import nl.tudelft.ipv8.Community

/**
 * A community that is used only to signal support for TFTP transport. It does not implement
 * any messaging and should not use any discovery strategies.
 */
class TFTPCommunity : Community() {
    override val serviceId = SERVICE_ID

    companion object {
        const val SERVICE_ID = "33688436558bab6d1794fe980a2c1441d1f1df88"
    }
}
