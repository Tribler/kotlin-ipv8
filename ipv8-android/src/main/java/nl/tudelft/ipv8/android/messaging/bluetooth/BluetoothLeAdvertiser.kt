package nl.tudelft.ipv8.android.messaging.bluetooth

import android.bluetooth.BluetoothManager
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.os.ParcelUuid
import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

class IPv8BluetoothLeAdvertiser(
    private val bluetoothManager: BluetoothManager
) {
    private val bluetoothAdapter = bluetoothManager.adapter

    private val leAdvertiser by lazy {
        bluetoothAdapter.bluetoothLeAdvertiser
    }

    private val advertiseCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
            logger.debug { "onStartSuccess" }
            isAdvertising = true
        }

        override fun onStartFailure(errorCode: Int) {
            logger.debug { "onStartFailure $errorCode" }
            isAdvertising = false
        }
    }

    private var isAdvertising = false

    fun start() {
        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_POWER)
            .setTimeout(0)
            // make sure the server is connectable!
            .setConnectable(true)
            .build()

        val mid = "5ad767b05ae592a02488272ca2a86b847d4562e1"

        val advertiseData = AdvertiseData.Builder()
            .setIncludeDeviceName(false)
            .addServiceUuid(ParcelUuid(GattServerManager.SERVICE_UUID))
            // TODO: Add my mid as service data
            .addServiceData(ParcelUuid(GattServerManager.ADVERTISE_IDENTITY_UUID),
                mid.toByteArray(Charsets.US_ASCII))
            .build()

        leAdvertiser?.startAdvertising(settings, advertiseData, advertiseCallback)
    }

    fun stop() {
        leAdvertiser?.stopAdvertising(advertiseCallback)
    }
}
