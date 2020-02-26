package nl.tudelft.ipv8.android.messaging.bluetooth

import android.bluetooth.*
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.util.Log
import kotlinx.coroutines.*
import mu.KotlinLogging
import nl.tudelft.ipv8.Overlay
import nl.tudelft.ipv8.android.IPv8Android
import nl.tudelft.ipv8.messaging.bluetooth.BluetoothAddress
import nl.tudelft.ipv8.peerdiscovery.strategy.DiscoveryStrategy

private val logger = KotlinLogging.logger {}

class BluetoothLeDiscovery(
    private val context: Context,
    private val overlay: Overlay,
    private val bluetoothManager: BluetoothManager,

    /**
     * The maximum number of peers we should connect to over Bluetooth.
     */
    private val peers: Int = 7
) : DiscoveryStrategy {
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val adapter = bluetoothManager.adapter
    private val leScanner by lazy {
        adapter.bluetoothLeScanner
    }

    private val scanCallback = object : ScanCallback() {
        override fun onBatchScanResults(results: MutableList<ScanResult>) {
            logger.debug { "onBatchScanResults: ${results.size} results" }
        }

        override fun onScanFailed(errorCode: Int) {
            logger.error { "onScanFailed: $errorCode" }
            isScanning = false
        }

        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device
            //val success = device.fetchUuidsWithSdp()
            //Log.d(TAG, "onScanResult $callbackType " + device.address + " " + device.name + " " + success + " " + device.uuids)
            val uuids = result.scanRecord?.serviceUuids?.map {
                it.uuid
            } ?: listOf()

            // logger.debug { "onScanResult $callbackType " + device.address + " " + device.name + " " + uuids }

            if (uuids.contains(IPv8GattServer.SERVICE_UUID)) {
                logger.debug { "Discovered Bluetooth device: ${device.address}" }
                overlay.network.discoverBluetoothAddress(BluetoothAddress(device.address))
            }
        }
    }

    private var isScanning = false

    private var scanJob: Job? = null

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
            startScan()
        } else {
            logger.debug { "Bluetooth not enabled" }
            // TODO: start scan once Bluetooth is enabled
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
        stopScan()
        scanJob?.cancel()
    }

    private fun startScan() {
        logger.debug { "startScan" }

        isScanning = true

        val settingsBuilder = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_BALANCED)
            .setMatchMode(ScanSettings.MATCH_MODE_AGGRESSIVE)
            .setNumOfMatches(ScanSettings.MATCH_NUM_MAX_ADVERTISEMENT)
            .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)

        /*
        val serviceScanFilter = ScanFilter.Builder()
            .setServiceUuid(ParcelUuid(SERVICE_UUID))
            .build()
         */

        leScanner.startScan(null, settingsBuilder.build(), scanCallback)

        // TODO: Stop scanning after a pre-defined scan period.
        /*
        scanJob = scope.launch {
            if (isActive) {
                delay(SCAN_PERIOD_MILLIS)
                stopScan()
            }
        }
         */
    }

    private fun stopScan() {
        logger.debug { "stopScan" }

        isScanning = false
        leScanner.stopScan(scanCallback)
        scanJob?.cancel()
    }

    companion object {
        private const val SCAN_PERIOD_MILLIS = 60_000L
    }

    class Factory(
        private val context: Context,
        private val bluetoothManager: BluetoothManager
    ) : DiscoveryStrategy.Factory<BluetoothLeDiscovery>() {
        override fun create(): BluetoothLeDiscovery {
            return BluetoothLeDiscovery(context, getOverlay(), bluetoothManager)
        }
    }
}
