package nl.tudelft.ipv8

import kotlinx.coroutines.*
import nl.tudelft.ipv8.messaging.EndpointAggregator
import nl.tudelft.ipv8.peerdiscovery.Network
import nl.tudelft.ipv8.peerdiscovery.strategy.DiscoveryStrategy
import java.lang.IllegalStateException
import kotlin.math.roundToLong

class IPv8(
    private val endpoint: EndpointAggregator,
    private val configuration: IPv8Configuration,
    val myPeer: Peer,
    val network: Network = Network()
) {
    private val overlayLock = Object()

    val overlays = mutableMapOf<Class<out Overlay>, Overlay>()
    private val strategies = mutableListOf<DiscoveryStrategy>()

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var loopingCallJob: Job? = null

    private var isStarted = false

    fun isStarted(): Boolean {
        return isStarted
    }

    inline fun <reified T : Overlay> getOverlay(): T? {
        return overlays[T::class.java] as? T
    }

    fun start() {
        if (isStarted()) throw IllegalStateException("IPv8 has already started")

        isStarted = true

        endpoint.open()

        // Init overlays and discovery strategies
        for (overlayConfiguration in configuration.overlays) {
            val overlayClass = overlayConfiguration.factory.overlayClass
            if (overlays[overlayClass] != null) {
                throw IllegalArgumentException("Overlay $overlayClass already exists")
            }

            // Create an overlay instance and set all dependencies
            val overlay = overlayConfiguration.factory.create()
            overlay.myPeer = myPeer
            overlay.endpoint = endpoint
            overlay.network = network
            overlay.maxPeers = overlayConfiguration.maxPeers
            overlay.load()

            overlays[overlayClass] = overlay

            for (strategyFactory in overlayConfiguration.walkers) {
                val strategy = strategyFactory
                    .setOverlay(overlay)
                    .create()
                strategy.load()
                strategies.add(strategy)
            }
        }

        // Start looping call
        startLoopingCall()
    }

    private fun onTick() {
        if (endpoint.isOpen()) {
            synchronized(overlayLock) {
                for (strategy in strategies) {
                    try {
                        // Strategies are prone to programmer error
                        strategy.takeStep()
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        }
    }

    private fun startLoopingCall() {
        val interval = (configuration.walkerInterval * 1000).roundToLong()
        loopingCallJob = scope.launch {
            while (true) {
                onTick()
                delay(interval)
            }
        }
    }

    fun stop() {
        if (!isStarted()) throw IllegalStateException("IPv8 is not running")

        synchronized(overlayLock) {
            loopingCallJob?.cancel()

            for ((_, overlay) in overlays) {
                overlay.unload()
            }
            overlays.clear()

            for (strategy in strategies) {
                strategy.unload()
            }
            strategies.clear()

            endpoint.close()
        }

        isStarted = false
    }
}
