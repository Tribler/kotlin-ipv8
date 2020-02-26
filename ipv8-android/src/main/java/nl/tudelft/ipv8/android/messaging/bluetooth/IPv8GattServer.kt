package nl.tudelft.ipv8.android.messaging.bluetooth

import android.bluetooth.*
import android.content.Context
import mu.KotlinLogging
import nl.tudelft.ipv8.Peer
import nl.tudelft.ipv8.messaging.EndpointListener
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

    private var gattServer: BluetoothGattServer? = null

    private val buffers: MutableMap<String, ByteArray> = mutableMapOf()
    private val bufferSizes: MutableMap<String, Int> = mutableMapOf()

    private val gattServerCallback = object : BluetoothGattServerCallback() {
        override fun onConnectionStateChange(device: BluetoothDevice, status: Int, newState: Int) {
            logger.debug { "onConnectionStateChange: $device $newState" }
            // TODO: add/remove peer?
        }

        override fun onCharacteristicWriteRequest(
            device: BluetoothDevice,
            requestId: Int,
            characteristic: BluetoothGattCharacteristic,
            preparedWrite: Boolean,
            responseNeeded: Boolean,
            offset: Int,
            value: ByteArray?
        ) {
            val buffer = buffers[device.address] ?: ByteArray(1500)
            buffers[device.address] = buffer

            val bufferSize = bufferSizes[device.address] ?: 0

            if (value != null) {
                logger.debug { "onCharacteristicWriteRequest: $device $requestId ${characteristic.uuid} $preparedWrite $responseNeeded $offset ${value.size}"}

                for ((index, byte) in value.withIndex()) {
                    buffer[offset + index] = byte
                }

                bufferSizes[device.address] = max(offset + value.size, bufferSize)

                gattServer?.sendResponse(device, requestId, STATUS_OK, offset, value)
            }
        }

        override fun onExecuteWrite(device: BluetoothDevice, requestId: Int, execute: Boolean) {
            logger.debug { "onExecuteWrite ${device.address} $requestId $execute" }

            if (execute) {
                val buffer = buffers[device.address]
                val bufferSize = bufferSizes[device.address] ?: 0
                if (buffer != null) {
                    val data = buffer.copyOf(bufferSize)
                    val packet = Packet(BluetoothAddress(device.address), data)
                    logger.debug { "Received packet: (${data.size} B) " + data.toHex() }
                    packetListener?.invoke(packet)
                    gattServer?.sendResponse(device, requestId, STATUS_OK, data.size, data)
                }
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

            // TODO: do we even need to support reading?
            // val value = messages.value?.lastOrNull()?.message?.toByteArray()
            // gattServer?.sendResponse(device, requestId, STATUS_OK, offset, value)

            if (characteristic.uuid == IDENTITY_CHARACTERISTIC_UUID) {
                val publicKeyBin = myPeer.publicKey.keyToBin()
                gattServer?.sendResponse(device, requestId, STATUS_OK, offset,
                    publicKeyBin.copyOfRange(offset, publicKeyBin.size))
            }
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

    private fun createGattService(): BluetoothGattService {
        val service = BluetoothGattService(SERVICE_UUID,
            BluetoothGattService.SERVICE_TYPE_PRIMARY)

        val properties = BluetoothGattCharacteristic.PROPERTY_READ or
            BluetoothGattCharacteristic.PROPERTY_NOTIFY or
            BluetoothGattCharacteristic.PROPERTY_WRITE
        val permissions = BluetoothGattCharacteristic.PERMISSION_READ or
            BluetoothGattCharacteristic.PERMISSION_WRITE
        val characteristic = BluetoothGattCharacteristic(
            CHARACTERISTIC_UUID, properties, permissions
        )
        service.addCharacteristic(characteristic)

        val identityCharacteristic = BluetoothGattCharacteristic(
            IDENTITY_CHARACTERISTIC_UUID,
            BluetoothGattCharacteristic.PROPERTY_READ,
            BluetoothGattCharacteristic.PERMISSION_READ
        )
        // TODO: do we need to set value here?
        identityCharacteristic.value = myPeer.publicKey.keyToBin()
        service.addCharacteristic(identityCharacteristic)

        return service
    }

    companion object {
        val SERVICE_UUID = UUID.fromString("62c94792-5e72-461b-bbf4-4be7360776b5")
        val CHARACTERISTIC_UUID: UUID = UUID.fromString("00002a2b-0000-1000-8000-00805f9b34fb")
        val IDENTITY_CHARACTERISTIC_UUID: UUID = UUID.fromString("10002a2b-0000-1000-8000-00805f9b34fb")

        private const val STATUS_OK = 0
    }
}
