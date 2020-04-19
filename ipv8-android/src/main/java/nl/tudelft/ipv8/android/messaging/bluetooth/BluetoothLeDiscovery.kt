package nl.tudelft.ipv8.android.messaging.bluetooth

import android.bluetooth.*
import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import mu.KotlinLogging
import nl.tudelft.ipv8.Overlay
import nl.tudelft.ipv8.messaging.bluetooth.BluetoothAddress
import nl.tudelft.ipv8.peerdiscovery.strategy.DiscoveryStrategy

private val logger = KotlinLogging.logger {}

class BluetoothLeDiscovery(
    private val overlay: Overlay,
    bluetoothManager: BluetoothManager,
    private val scanner: IPv8BluetoothLeScanner,

    /**
     * The maximum number of peers we should connect to over Bluetooth.
     */
    private val peers: Int = 7
) : DiscoveryStrategy {
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private val adapter = bluetoothManager.adapter

    override fun load() {
        if (adapter == null) {
            logger.error { "BluetoothAdapter is not available" }
            return
        }

        if (!adapter.isEnabled) {
            // Enable Bluetooth if not enabled
            logger.debug { "Bluetooth disabled, enabling..." }
            adapter.enable()
        }

        if (adapter.isEnabled) {
            // startScan()
            scanner.startScan()
        } else {
            logger.debug { "Bluetooth not enabled" }
            // TODO: start scan once Bluetooth is enabled
        }

        scope.launch {
            scanner.scanResult.collect { result ->
                val device = result.device
                val bluetoothAddress = BluetoothAddress(device.address)
                overlay.network.discoverBluetoothAddress(bluetoothAddress)
            }
        }
    }

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

    override fun unload() {
        scanner.stopScan()
    }

    companion object {
        private const val SCAN_PERIOD_MILLIS = 60_000L
    }

    class Factory(
        private val context: Context,
        private val bluetoothManager: BluetoothManager
    ) : DiscoveryStrategy.Factory<BluetoothLeDiscovery>() {
        override fun create(): BluetoothLeDiscovery {
            val leScanner = IPv8BluetoothLeScanner(bluetoothManager)
            return BluetoothLeDiscovery(getOverlay(), bluetoothManager, leScanner)
        }
    }
}
