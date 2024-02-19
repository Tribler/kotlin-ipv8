package nl.tudelft.ipv8.android.messaging.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.le.*
import android.os.ParcelUuid
import kotlinx.coroutines.*
import mu.KotlinLogging
import nl.tudelft.ipv8.android.messaging.bluetooth.GattServerManager.Companion.SERVICE_UUID
import nl.tudelft.ipv8.messaging.bluetooth.BluetoothAddress
import nl.tudelft.ipv8.messaging.bluetooth.BluetoothPeerCandidate
import nl.tudelft.ipv8.peerdiscovery.Network
import java.lang.Exception

private val logger = KotlinLogging.logger {}

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class IPv8BluetoothLeScanner(
    private val bluetoothManager: BluetoothManager,
    private val network: Network,
) {
    private var isScanning = false
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var scanJob: Job? = null

    private val leScanner: BluetoothLeScanner by lazy {
        // BluetoothLeScanner is only available when Bluetooth is turned on
        bluetoothManager.adapter.bluetoothLeScanner
    }

    private val scanCallback =
        object : ScanCallback() {
            override fun onBatchScanResults(results: MutableList<ScanResult>) {
                logger.debug { "onBatchScanResults: ${results.size} results" }
            }

            override fun onScanFailed(errorCode: Int) {
                logger.error { "onScanFailed: $errorCode" }
                isScanning = false
            }

            override fun onScanResult(
                callbackType: Int,
                result: ScanResult,
            ) {
                val device = result.device
                val identity =
                    result.scanRecord?.serviceData
                        ?.get(ParcelUuid(GattServerManager.ADVERTISE_IDENTITY_UUID))
                val rssi = result.rssi
                val txPowerLevel = result.scanRecord?.txPowerLevel

                val uuids =
                    result.scanRecord?.serviceUuids?.map {
                        it.uuid
                    } ?: listOf()

                if (uuids.contains(SERVICE_UUID)) {
                    logger.debug { "Discovered Bluetooth device: ${device.address}" }
                    val bluetoothAddress = BluetoothAddress(device.address)
                    val peer =
                        BluetoothPeerCandidate(
                            identity?.toString(Charsets.US_ASCII),
                            bluetoothAddress,
                            txPowerLevel,
                            rssi,
                        )
                    network.discoverBluetoothPeer(peer)
                }
            }
        }

    @SuppressLint("MissingPermission") // TODO: Fix permission usage.
    fun start() {
        logger.debug { "startScan" }

        isScanning = true

        val settingsBuilder =
            ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_POWER)

        val serviceScanFilter =
            ScanFilter.Builder()
                .setServiceUuid(ParcelUuid(SERVICE_UUID))
                .build()

        leScanner.startScan(listOf(serviceScanFilter), settingsBuilder.build(), scanCallback)
    }

    @SuppressLint("MissingPermission") // TODO: Fix permission usage.
    fun stop() {
        logger.debug { "stopScan" }

        isScanning = false

        try {
            if (bluetoothManager.adapter?.isEnabled == true) {
                leScanner.stopScan(scanCallback)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Starts a periodic scan where each scan window takes [duration] ms and there is [pause] ms
     * long pause between scans.
     */
    fun startPeriodicScan(
        duration: Long,
        pause: Long,
    ) {
        scanJob =
            scope.launch {
                while (isActive) {
                    if (bluetoothManager.adapter.isEnabled) {
                        start()
                        delay(duration)
                        stop()
                    } else {
                        logger.warn { "Bluetooth is not enabled" }
                    }
                    delay(pause)
                }
            }
    }

    /**
     * Stops the periodic scan.
     */
    fun stopPeriodicScan() {
        scanJob?.cancel()
        scanJob = null
    }
}
