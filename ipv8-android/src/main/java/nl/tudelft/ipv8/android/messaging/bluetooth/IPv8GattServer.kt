package nl.tudelft.ipv8.android.messaging.bluetooth

import android.bluetooth.*
import android.content.Context
import mu.KotlinLogging
import nl.tudelft.ipv8.Peer
import nl.tudelft.ipv8.messaging.Packet
import nl.tudelft.ipv8.messaging.bluetooth.BluetoothAddress
import nl.tudelft.ipv8.util.toHex
import java.util.*
import kotlin.math.max

private val logger = KotlinLogging.logger {}

class IPv8GattServer(
    private val context: Context,
    private val bluetoothManager: BluetoothManager,
    private val myPeer: Peer
) {
    var packetListener: ((Packet) -> Unit)? = null
    var connectionListener: ((BluetoothDevice, Int) -> Unit)? = null

    private var gattServer: BluetoothGattServer? = null

    private val buffers: MutableMap<String, ByteArray> = mutableMapOf()
    private val bufferSizes: MutableMap<String, Int> = mutableMapOf()

    // private val connectedDevices: MutableMap<String, BluetoothDevice> = mutableMapOf()

    private val readCharacteristic = BluetoothGattCharacteristic(
        READ_CHARACTERISTIC_UUID,
        BluetoothGattCharacteristic.PROPERTY_WRITE,
        BluetoothGattCharacteristic.PERMISSION_WRITE
    )

    private val gattServerCallback = object : BluetoothGattServerCallback() {
        override fun onConnectionStateChange(device: BluetoothDevice, status: Int, newState: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                logger.debug { "onConnectionStateChange: $device $newState" }
                connectionListener?.invoke(device, newState)

                /*
                when (newState) {
                    BluetoothProfile.STATE_CONNECTED ->
                        connectedDevices[device.address] = device
                    BluetoothProfile.STATE_DISCONNECTED ->
                        connectedDevices.remove(device.address)
                }
                */
            }
        }

        override fun onServiceAdded(status: Int, service: BluetoothGattService) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                logger.debug { "Service has been added: ${service.uuid}" }
            } else {
                logger.error { "Service failed to be added: ${service.uuid}" }
            }
        }

        override fun onCharacteristicWriteRequest(
            device: BluetoothDevice,
            requestId: Int,
            characteristic: BluetoothGattCharacteristic,
            preparedWrite: Boolean,
            responseNeeded: Boolean,
            offset: Int,
            value: ByteArray
        ) {
            if (characteristic.uuid == WRITE_CHARACTERISTIC_UUID) {
                val buffer = buffers[device.address] ?: ByteArray(1500)
                buffers[device.address] = buffer

                val bufferSize = bufferSizes[device.address] ?: 0

                logger.debug { "onCharacteristicWriteRequest: $device $requestId ${characteristic.uuid} $preparedWrite $responseNeeded $offset ${value.size}" }

                for ((index, byte) in value.withIndex()) {
                    buffer[offset + index] = byte
                }

                bufferSizes[device.address] = max(offset + value.size, bufferSize)

                gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, value)
            } else {
                gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_REQUEST_NOT_SUPPORTED, offset, null)
            }
        }

        override fun onExecuteWrite(device: BluetoothDevice, requestId: Int, execute: Boolean) {
            logger.debug { "onExecuteWrite ${device.address} $requestId $execute" }

            if (execute) {
                val buffer = buffers[device.address]
                val bufferSize = bufferSizes[device.address]
                if (buffer != null && bufferSize != null) {
                    val data = buffer.copyOf(bufferSize)
                    val packet = Packet(BluetoothAddress(device.address), data)
                    logger.debug { "Received packet: (${data.size} B) " + data.toHex() }
                    gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, data.size, data)
                    packetListener?.invoke(packet)
                } else {
                    gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_FAILURE, 0, null)
                }
            } else {
                gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, null)
            }

            // Cancel write request
            buffers.remove(device.address)
            bufferSizes.remove(device.address)
        }

        override fun onCharacteristicReadRequest(
            device: BluetoothDevice,
            requestId: Int,
            offset: Int,
            characteristic: BluetoothGattCharacteristic
        ) {
            logger.debug { "onCharacteristicReadRequest: $device $requestId $offset ${characteristic.uuid}" }

            if (characteristic.uuid == IDENTITY_CHARACTERISTIC_UUID) {
                val publicKeyBin = myPeer.publicKey.keyToBin()
                gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset,
                    publicKeyBin.copyOfRange(offset, publicKeyBin.size))
            } else if (characteristic.uuid == READ_CHARACTERISTIC_UUID) {
                // TODO: get data of current packet
            }
        }

        override fun onNotificationSent(device: BluetoothDevice, status: Int) {
            logger.debug { "onNotificationSent: $device $status" }
            // TODO: wait for this callback before sending next notification
        }
    }

    fun open() {
        gattServer = bluetoothManager.openGattServer(context, gattServerCallback)
        val addedService = gattServer?.addService(createGattService())
        logger.debug { "Request to add service successful? $addedService" }
    }

    fun close() {
        gattServer?.close()
    }

    /*
    fun send(address: BluetoothAddress, data: ByteArray): Boolean {
        val device = connectedDevices[address.mac]
        val gattServer = gattServer
        return if (device != null && gattServer != null) {
            gattServer.notifyCharacteristicChanged(device, readCharacteristic, true)
        } else {
            false
        }
    }
     */

    private fun createGattService(): BluetoothGattService {
        val service = BluetoothGattService(SERVICE_UUID,
            BluetoothGattService.SERVICE_TYPE_PRIMARY)

        val writeCharacteristic = BluetoothGattCharacteristic(
            WRITE_CHARACTERISTIC_UUID,
            BluetoothGattCharacteristic.PROPERTY_WRITE,
            BluetoothGattCharacteristic.PERMISSION_WRITE
        )
        service.addCharacteristic(writeCharacteristic)

        service.addCharacteristic(readCharacteristic)

        val identityCharacteristic = BluetoothGattCharacteristic(
            IDENTITY_CHARACTERISTIC_UUID,
            BluetoothGattCharacteristic.PROPERTY_READ,
            BluetoothGattCharacteristic.PERMISSION_READ
        )
        // TODO: do we need to set value here?
        // identityCharacteristic.value = myPeer.publicKey.keyToBin()
        service.addCharacteristic(identityCharacteristic)

        return service
    }

    companion object {
        val SERVICE_UUID: UUID =
            UUID.fromString("62c94792-5e72-461b-bbf4-4be7360776b5")
        val WRITE_CHARACTERISTIC_UUID: UUID =
            UUID.fromString("00002a2b-0000-1000-8000-00805f9b34fb")
        val READ_CHARACTERISTIC_UUID: UUID =
            UUID.fromString("20002a2b-0000-1000-8000-00805f9b34fb")
        val IDENTITY_CHARACTERISTIC_UUID: UUID =
            UUID.fromString("10002a2b-0000-1000-8000-00805f9b34fb")
    }
}
