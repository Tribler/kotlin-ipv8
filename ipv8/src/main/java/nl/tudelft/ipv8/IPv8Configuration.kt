package nl.tudelft.ipv8

import nl.tudelft.ipv8.messaging.EndpointAggregator
import nl.tudelft.ipv8.peerdiscovery.Network
import nl.tudelft.ipv8.peerdiscovery.strategy.DiscoveryStrategy

class IPv8Configuration(
    val address: String = "0.0.0.0",
    val port: Int = 8090,
    val walkerInterval: Double = 5.0,
    val overlays: List<OverlayConfiguration<*>>
)

open class OverlayConfiguration<T : Overlay>(
    val factory: Overlay.Factory<T>,
    val walkers: List<DiscoveryStrategy.Factory<*>>,
    val maxPeers: Int = 30
)

class SecondaryOverlayConfiguration<T : Overlay>(
    factory: Overlay.Factory<T>,
    walkers: List<DiscoveryStrategy.Factory<*>>,
    maxPeers: Int = 30,
    val myPeer: Peer? = null,
    val endpoint: EndpointAggregator? = null,
    val network: Network? = null
) : OverlayConfiguration<T>(factory, walkers, maxPeers)
