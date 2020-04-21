package nl.tudelft.ipv8.android.messaging.bluetooth

import mu.KotlinLogging
import nl.tudelft.ipv8.Overlay
import nl.tudelft.ipv8.peerdiscovery.strategy.DiscoveryStrategy

private val logger = KotlinLogging.logger {}

class BluetoothLeDiscovery(
    private val overlay: Overlay,

    /**
     * The maximum number of peers we should connect to over Bluetooth.
     */
    private val peers: Int = 7
) : DiscoveryStrategy {
    override fun takeStep() {
        val bluetoothPeers = overlay.network.verifiedPeers.filter {
            it.bluetoothAddress != null
        }

        if (bluetoothPeers.size >= peers) return

        val addresses = overlay.network.getConnectableBluetoothAddresses()
        if (addresses.isNotEmpty()) {
            val address = addresses.random()
            overlay.endpoint.connectTo(address)
        }
    }
}
