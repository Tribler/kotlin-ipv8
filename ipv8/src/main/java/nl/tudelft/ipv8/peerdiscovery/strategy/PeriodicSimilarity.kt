package nl.tudelft.ipv8.peerdiscovery.strategy

import nl.tudelft.ipv8.peerdiscovery.DiscoveryCommunity

/**
 * On every step, it sends a similarity request to a random peer.
 */
class PeriodicSimilarity(
    override val overlay: DiscoveryCommunity
) : DiscoveryStrategy {
    override fun takeStep() {
        val peer = overlay.network.getRandomPeer()
        if (peer != null) {
            overlay.sendSimilarityRequest(peer)
        }
    }

    class Factory : DiscoveryStrategy.Factory<PeriodicSimilarity>() {
        override fun create(): PeriodicSimilarity {
            val overlay = getOverlay() as? DiscoveryCommunity
                ?: throw IllegalStateException("PeriodicSimilarity is only compatible with " +
                    "DiscoveryCommunity")
            return PeriodicSimilarity(overlay)
        }
    }
}
