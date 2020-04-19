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

private val logger = KotlinLogging.logger {}

@UseExperimental(ExperimentalCoroutinesApi::class, FlowPreview::class)
class IPv8BluetoothLeScanner(
    private val bluetoothManager: BluetoothManager
) {
    private var isScanning = false
    // private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    //private var scanJob: Job? = null

    private val leScanner: BluetoothLeScanner by lazy {
        // BluetoothLeScanner is only available when Bluetooth is turned on
        bluetoothManager.adapter.bluetoothLeScanner
    }

    private val _scanResult = BroadcastChannel<BluetoothLeScanResult>(Channel.BUFFERED)
    val scanResult = _scanResult.asFlow()

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
                // overlay.network.discoverBluetoothAddress(BluetoothAddress(device.address))
                _scanResult.offer(BluetoothLeScanResult(
                    device,
                    identity,
                    txPowerLevel,
                    rssi
                ))
            }
        }
    }

    fun startScan() {
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

    fun stopScan() {
        logger.debug { "stopScan" }

        isScanning = false
        leScanner.stopScan(scanCallback)
        //scanJob?.cancel()
    }

    data class BluetoothLeScanResult(
        val device: BluetoothDevice,
        val identity: ByteArray?,
        val txPowerLevel: Int?,
        val rssi: Int
    )
}
