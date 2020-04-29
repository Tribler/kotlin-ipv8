package nl.tudelft.ipv8.messaging

import mu.KotlinLogging
import nl.tudelft.ipv8.IPv4Address

private val logger = KotlinLogging.logger {}

abstract class Endpoint<A> {
    private val listeners = mutableListOf<EndpointListener>()
    private var estimatedLan: IPv4Address? = null

    fun addListener(listener: EndpointListener) {
        listeners.add(listener)

        val estimatedLan = estimatedLan
        if (estimatedLan != null) {
            listener.onEstimatedLanChanged(estimatedLan)
        }
    }

    fun removeListener(listener: EndpointListener) {
        listeners.remove(listener)
    }

    protected fun notifyListeners(packet: Packet) {
        for (listener in listeners) {
            try {
                listener.onPacket(packet)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    protected fun setEstimatedLan(address: IPv4Address) {
        logger.info("Estimated LAN address: $address")

        estimatedLan = address

        for (listener in listeners) {
            listener.onEstimatedLanChanged(address)
        }
    }

    abstract fun isOpen(): Boolean
    abstract fun send(peer: A, data: ByteArray)
    abstract fun open()
    abstract fun close()
}
