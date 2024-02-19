package nl.tudelft.ipv8.android.messaging.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.os.ParcelUuid
import mu.KotlinLogging
import nl.tudelft.ipv8.Peer
import nl.tudelft.ipv8.util.hexToBytes

private val logger = KotlinLogging.logger {}

class IPv8BluetoothLeAdvertiser(
    bluetoothManager: BluetoothManager,
) {
    private val bluetoothAdapter = bluetoothManager.adapter

    private val leAdvertiser by lazy {
        bluetoothAdapter.bluetoothLeAdvertiser
    }

    private val advertiseCallback =
        object : AdvertiseCallback() {
            override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
                logger.debug { "onStartSuccess" }
                isAdvertising = true
            }

            override fun onStartFailure(errorCode: Int) {
                logger.error { "onStartFailure $errorCode" }
                isAdvertising = false
            }
        }

    private var isAdvertising = false

    @SuppressLint("MissingPermission") // TODO: Fix permission usage.
    fun start(myPeer: Peer) {
        val settings =
            AdvertiseSettings.Builder()
                .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_POWER)
                .setTimeout(0)
                // make sure the server is connectable!
                .setConnectable(true)
                .build()

        val advertiseData = getAdvertiseData()
        val scanResponse = getScanResponse(myPeer)

        leAdvertiser?.startAdvertising(settings, advertiseData, scanResponse, advertiseCallback)
    }

    @SuppressLint("MissingPermission") // TODO: Fix permission usage.
    fun stop() {
        leAdvertiser?.stopAdvertising(advertiseCallback)
    }

    private fun getAdvertiseData(): AdvertiseData {
        return AdvertiseData.Builder()
            .setIncludeDeviceName(false)
            .addServiceUuid(ParcelUuid(GattServerManager.SERVICE_UUID))
            .build()
    }

    private fun getScanResponse(myPeer: Peer): AdvertiseData {
        return AdvertiseData.Builder()
            .setIncludeDeviceName(false)
            .addServiceData(
                ParcelUuid(GattServerManager.ADVERTISE_IDENTITY_UUID),
                myPeer.mid.hexToBytes(),
            )
            .build()
    }
}
