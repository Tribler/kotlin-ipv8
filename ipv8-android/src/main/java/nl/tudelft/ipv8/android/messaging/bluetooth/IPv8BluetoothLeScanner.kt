package nl.tudelft.ipv8.android.messaging.bluetooth

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.os.ParcelUuid
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BroadcastChannel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.asFlow
import mu.KotlinLogging
import nl.tudelft.ipv8.messaging.bluetooth.BluetoothAddress
import nl.tudelft.ipv8.peerdiscovery.Network

private val logger = KotlinLogging.logger {}

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class IPv8BluetoothLeScanner(
    private val bluetoothManager: BluetoothManager,
    private val network: Network
) {
    private var isScanning = false
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    //private var scanJob: Job? = null

    private val leScanner: BluetoothLeScanner by lazy {
        // BluetoothLeScanner is only available when Bluetooth is turned on
        bluetoothManager.adapter.bluetoothLeScanner
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
            val identity = result.scanRecord?.serviceData
                ?.get(ParcelUuid(GattServerManager.ADVERTISE_IDENTITY_UUID))
            val rssi = result.rssi
            val txPowerLevel = result.scanRecord?.txPowerLevel

            val uuids = result.scanRecord?.serviceUuids?.map {
                it.uuid
            } ?: listOf()

            if (uuids.contains(GattServerManager.SERVICE_UUID)) {
                logger.debug { "Discovered Bluetooth device: ${device.address}" }
                val bluetoothAddress = BluetoothAddress(device.address)
                network.discoverBluetoothAddress(bluetoothAddress)
            }
        }
    }

    fun start() {
        logger.debug { "startScan" }

        isScanning = true

        val settingsBuilder = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_POWER)
            .setMatchMode(ScanSettings.MATCH_MODE_AGGRESSIVE)
            .setNumOfMatches(ScanSettings.MATCH_NUM_MAX_ADVERTISEMENT)
            .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)

        /*
        val serviceScanFilter = ScanFilter.Builder()
            .setServiceUuid(ParcelUuid(SERVICE_UUID))
            .build()
         */

        leScanner.startScan(null, settingsBuilder.build(), scanCallback)

        // TODO: Stop scanning after scan period
        /*
        scanJob = scope.launch {
            if (isActive) {
                delay(SCAN_PERIOD_MILLIS)
                stopScan()
            }
        }
        */
    }

    fun stop() {
        logger.debug { "stopScan" }

        isScanning = false
        leScanner.stopScan(scanCallback)
        //scanJob?.cancel()
    }

    /**
     * Starts a periodic scan where each scan window takes [duration] ms and there is [pause] ms
     * long pause between scans.
     */
    fun startPeriodicScan(duration: Long, pause: Long) {
        scope.launch {
            while (isActive) {
                if (bluetoothManager.adapter.isEnabled) {
                    start()
                    delay(duration)
                    stop()
                    delay(pause)
                } else {
                    logger.warn { "Bluetooth is not enabled" }
                }
            }
        }
    }
}
