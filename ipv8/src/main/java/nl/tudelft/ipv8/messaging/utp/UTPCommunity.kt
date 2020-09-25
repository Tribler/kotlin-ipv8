package nl.tudelft.ipv8.messaging.utp

import nl.tudelft.ipv8.Community

/**
 * A community that is used only to signal support for UTP transport. It does not implement
 * any messaging and should not use any discovery strategies.
 */
class UTPCommunity : Community() {
    override val serviceId = SERVICE_ID

    companion object {
        const val SERVICE_ID = "aa4cd4f2b7c5d1dd54115acc54295b8d9f2eec5d"
    }
}
