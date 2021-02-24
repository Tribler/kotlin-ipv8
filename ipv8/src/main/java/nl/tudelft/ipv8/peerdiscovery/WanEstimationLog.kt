package nl.tudelft.ipv8.peerdiscovery

import nl.tudelft.ipv8.IPv4Address
import nl.tudelft.ipv8.messaging.payload.ConnectionType
import java.util.*

/**
 * The WAN estimation log keeps track of our public address that is reported by other peers.
 * The most recent values are used to determine our WAN address used in introduction requests.
 * Further behavioral NAT analysis is performed to detect symmetric NAT behavior.
 */
class WanEstimationLog {
    private val log = mutableListOf<WanLogItem>()

    /**
     * Adds a new item to the log if this WAN is not reported by this sender yet.
     */
    @Synchronized
    fun addItem(item: WanLogItem) {
        val existingItem = log.findLast {
            it.wan == item.wan && it.sender == item.sender
        }
        if (existingItem == null) {
            log.add(item)
        }
    }

    /**
     * Estimates our current WAN address using the majority from the last few log items.
     * @return Our current WAN or null if there are no items in the log.
     */
    @Synchronized
    fun estimateWan(): IPv4Address? {
        val wans = log.takeLast(MAJORITY_INPUT_SIZE).map { it.wan }
        return majority(wans)
    }

    /**
     * Estimates our NAT type based on NAT behavior in the following fashion:
     * - public – our LAN address = WAN address, we are not behind a NAT
     * - symmetric – our WAN address is changed frequently, symmetric NAT like behavior
     * - unknown – most of the WAN reports are matching, NAT performs endpoint independent mapping
     */
    @Synchronized
    fun estimateConnectionType(): ConnectionType {
        val wans = log.map { it.wan }.distinct()
        val wan = estimateWan()
        val lan = log.lastOrNull()?.lan

        val symmetricProbability = (wans.size - 1) / log.size.toFloat()

        return when {
            wan != null && wan == lan -> ConnectionType.PUBLIC
            log.size > 1 && symmetricProbability > 0.1 -> ConnectionType.SYMMETRIC_NAT
            else -> ConnectionType.UNKNOWN
        }
    }

    /**
     * Clears the log. It should be called when the LAN address is changed.
     */
    @Synchronized
    fun clear() {
        log.clear()
    }

    @Synchronized
    fun getLog(): List<WanLogItem> {
        return log
    }

    /**
     * Returns the most frequently occurring item in the list, or null if the list is empty.
     */
    private fun <T> majority(items: List<T>): T? {
        val counts = mutableMapOf<T, Int>()
        for (item in items) {
            counts[item] = (counts[item] ?: 0) + 1
        }
        return counts.maxByOrNull { it.value }?.key
    }

    class WanLogItem(
        /**
         * The timestamp of when the address report was received.
         */
        val timestamp: Date,

        /**
         * The IP address of a peer that reported the address.
         */
        val sender: IPv4Address,

        /**
         * Our LAN address at the time the report has been received.
         */
        val lan: IPv4Address,

        /**
         * Our WAN address that was reported by the sender.
         */
        val wan: IPv4Address
    )

    companion object {
        private const val MAJORITY_INPUT_SIZE = 3
    }
}
