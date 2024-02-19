package nl.tudelft.ipv8.android.messaging.bluetooth

import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattServer
import android.content.Context
import android.util.Log
import mu.KotlinLogging
import nl.tudelft.ipv8.Peer
import nl.tudelft.ipv8.keyvault.defaultCryptoProvider
import nl.tudelft.ipv8.messaging.bluetooth.BluetoothAddress
import no.nordicsemi.android.ble.BleManager

private val logger = KotlinLogging.logger {}

class GattClientManager(context: Context) : BleManager<GattClientCallbacks>(context) {
    private var writeCharacteristic: BluetoothGattCharacteristic? = null
    private var identityCharacteristic: BluetoothGattCharacteristic? = null

    private var serverCharacteristic: BluetoothGattCharacteristic? = null

    override fun getGattCallback(): BleManagerGattCallback {
        return IPv8GattCallback()
    }

    override fun log(
        priority: Int,
        message: String,
    ) {
        Log.println(priority, "IPv8BleManager", message)
    }

    fun send(data: ByteArray) {
        val characteristic = writeCharacteristic
        if (characteristic != null) {
            writeCharacteristic(characteristic, data)
                // Split packets longer than MTU
                // .split()
                .enqueue()
        }
    }

    private inner class IPv8GattCallback : BleManagerGattCallback() {
        // This method will be called when the device is connected and services are discovered.
        // You need to obtain references to the characteristics and descriptors that you will use.
        // Return true if all required services are found, false otherwise.
        override fun isRequiredServiceSupported(gatt: BluetoothGatt): Boolean {
            logger.debug { "isRequiredServiceSupported" }

            val gattService = gatt.getService(GattServerManager.SERVICE_UUID)
            return if (gattService != null) {
                writeCharacteristic = gattService.getCharacteristic(GattServerManager.WRITE_CHARACTERISTIC_UUID)
                identityCharacteristic = gattService.getCharacteristic(GattServerManager.IDENTITY_CHARACTERISTIC_UUID)
                true
            } else {
                false
            }
        }

        override fun initialize() {
            logger.debug { "initialize" }

            // Get identity
            readCharacteristic(identityCharacteristic)
                .with { device, data ->
                    val value = data.value
                    if (value != null) {
                        val peer =
                            Peer(
                                bluetoothAddress = BluetoothAddress(device.address),
                                key = defaultCryptoProvider.keyFromPublicBin(value),
                            )
                        callbacks.onPeerDiscovered(peer)
                    }
                }
                .enqueue()
        }

        override fun onServerReady(server: BluetoothGattServer) {
            serverCharacteristic =
                server
                    .getService(GattServerManager.SERVICE_UUID)
                    .getCharacteristic(GattServerManager.WRITE_CHARACTERISTIC_UUID)

            setWriteCallback(serverCharacteristic)
                .with { device, data ->
                    logger.info { "Server write ${device.address} ${data.size()}" }
                    val value = data.value
                    if (value != null) {
                        callbacks.onPacketWrite(device, value)
                    }
                }
        }

        override fun onDeviceDisconnected() {
            logger.debug { "onDeviceDisconnected" }

            writeCharacteristic = null
            identityCharacteristic = null
        }
    }
}
