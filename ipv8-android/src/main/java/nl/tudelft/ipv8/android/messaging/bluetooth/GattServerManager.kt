package nl.tudelft.ipv8.android.messaging.bluetooth

import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattService
import android.content.Context
import android.util.Log
import nl.tudelft.ipv8.Peer
import no.nordicsemi.android.ble.BleServerManager
import java.util.*

class GattServerManager(
    context: Context,
    private val myPeer: Peer,
) : BleServerManager(context) {
    override fun log(
        priority: Int,
        message: String,
    ) {
        Log.println(priority, "GattServerManager", message)
    }

    override fun initializeServer(): List<BluetoothGattService> {
        return Collections.singletonList(
            service(
                SERVICE_UUID,
                characteristic(
                    WRITE_CHARACTERISTIC_UUID,
                    BluetoothGattCharacteristic.PROPERTY_WRITE,
                    BluetoothGattCharacteristic.PERMISSION_WRITE,
                ),
                characteristic(
                    IDENTITY_CHARACTERISTIC_UUID,
                    BluetoothGattCharacteristic.PROPERTY_READ,
                    BluetoothGattCharacteristic.PERMISSION_READ,
                    myPeer.publicKey.keyToBin(),
                ),
            ),
        )
    }

    companion object {
        val SERVICE_UUID: UUID =
            UUID.fromString("62c94792-5e72-461b-bbf4-4be7360776b5")
        val WRITE_CHARACTERISTIC_UUID: UUID =
            UUID.fromString("00002a2b-0000-1000-8000-00805f9b34fb")
        val IDENTITY_CHARACTERISTIC_UUID: UUID =
            UUID.fromString("10002a2b-0000-1000-8000-00805f9b34fb")
        val ADVERTISE_IDENTITY_UUID: UUID =
            UUID.fromString("10002a2b-0000-1000-8000-00805f9b34fb")
    }
}
