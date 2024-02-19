package nl.tudelft.ipv8.android.messaging.bluetooth

import android.bluetooth.BluetoothDevice
import nl.tudelft.ipv8.Peer
import no.nordicsemi.android.ble.BleManagerCallbacks

abstract class GattClientCallbacks : BleManagerCallbacks {
    abstract fun onPeerDiscovered(peer: Peer)

    abstract fun onPacketWrite(
        device: BluetoothDevice,
        data: ByteArray,
    )

    override fun onDeviceDisconnecting(device: BluetoothDevice) {
        // NOOP
    }

    override fun onDeviceConnected(device: BluetoothDevice) {
        // NOOP
    }

    override fun onDeviceNotSupported(device: BluetoothDevice) {
        // NOOP
    }

    override fun onBondingFailed(device: BluetoothDevice) {
        // NOOP
    }

    override fun onServicesDiscovered(
        device: BluetoothDevice,
        optionalServicesFound: Boolean,
    ) {
        // NOOP
    }

    override fun onBondingRequired(device: BluetoothDevice) {
        // NOOP
    }

    override fun onLinkLossOccurred(device: BluetoothDevice) {
        // NOOP
    }

    override fun onBonded(device: BluetoothDevice) {
        // NOOP
    }

    override fun onDeviceReady(device: BluetoothDevice) {
        // NOOP
    }

    override fun onDeviceConnecting(device: BluetoothDevice) {
        // NOOP
    }
}
