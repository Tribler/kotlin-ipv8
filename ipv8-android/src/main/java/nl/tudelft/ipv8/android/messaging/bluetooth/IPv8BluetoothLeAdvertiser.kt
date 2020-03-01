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

        val advertiseData = AdvertiseData.Builder()
            .setIncludeDeviceName(false)
            .addServiceUuid(ParcelUuid(IPv8GattServer.SERVICE_UUID))
            .build()

        leAdvertiser.startAdvertising(settings, advertiseData, advertiseCallback)
    }

    fun stop() {
        leAdvertiser.stopAdvertising(advertiseCallback)
    }
}
