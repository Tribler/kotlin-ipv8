package nl.tudelft.ipv8.android.messaging.bluetooth

import mu.KotlinLogging
import nl.tudelft.ipv8.Overlay
import nl.tudelft.ipv8.peerdiscovery.strategy.DiscoveryStrategy

private val logger = KotlinLogging.logger {}

/**
 * A strategy for selecting discovered Bluetooth peers we should connect to.
 */
class BluetoothLeDiscovery(
    private val overlay: Overlay,
    private val peers: Int,
) : DiscoveryStrategy {
    override fun takeStep() {
        val bluetoothPeers =
            overlay.network.verifiedPeers.filter {
                it.bluetoothAddress != null
            }

        if (bluetoothPeers.size >= peers) return

        val candidates = overlay.network.getNewBluetoothPeerCandidates()

        logger.debug { "Found ${candidates.size} Bluetooth peer candidates" }

        if (candidates.isNotEmpty()) {
            val selectedCandidate = candidates.maxByOrNull { it.rssi }
            if (selectedCandidate != null) {
                logger.debug {
                    "Connecting to ${selectedCandidate.address} with RSSI ${selectedCandidate.rssi}"
                }
                overlay.endpoint.connectTo(selectedCandidate.address)
            }
        }
    }

    /**
     * The maximum number of peers we should connect to over Bluetooth.
     */
    class Factory(
        private val peers: Int = 7,
    ) : DiscoveryStrategy.Factory<BluetoothLeDiscovery>() {
        override fun create(): BluetoothLeDiscovery {
            return BluetoothLeDiscovery(getOverlay(), peers)
        }
    }
}
