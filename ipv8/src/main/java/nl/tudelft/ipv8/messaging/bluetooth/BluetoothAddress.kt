package nl.tudelft.ipv8.messaging.bluetooth

import nl.tudelft.ipv8.messaging.Address

data class BluetoothAddress(
    val mac: String
) : Address {
    override fun toString(): String {
        return mac
    }
}
