package nl.tudelft.ipv8.android.messaging.bluetooth

import android.bluetooth.BluetoothManager
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.os.ParcelUuid
import mu.KotlinLogging
import nl.tudelft.ipv8.Peer

private val logger = KotlinLogging.logger {}

class IPv8BluetoothLeAdvertiser(
    bluetoothManager: BluetoothManager
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

    fun start(myPeer: Peer) {
        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_POWER)
            .setTimeout(0)
            // make sure the server is connectable!
            .setConnectable(true)
            .build()

        leAdvertiser?.startAdvertising(settings, getAdvertiseData(myPeer), advertiseCallback)
    }

    fun stop() {
        leAdvertiser?.stopAdvertising(advertiseCallback)
    }

    private fun getAdvertiseData(myPeer: Peer): AdvertiseData {
        return AdvertiseData.Builder()
            .setIncludeDeviceName(false)
            .addServiceUuid(ParcelUuid(GattServerManager.SERVICE_UUID))
            .addServiceData(
                ParcelUuid(GattServerManager.ADVERTISE_IDENTITY_UUID),
                myPeer.mid.toByteArray(Charsets.US_ASCII)
            )
            .build()
    }
}
