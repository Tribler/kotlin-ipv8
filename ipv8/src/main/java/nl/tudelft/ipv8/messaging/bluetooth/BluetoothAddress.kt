package nl.tudelft.ipv8.messaging.bluetooth

import nl.tudelft.ipv8.messaging.BaseAddress

data class BluetoothAddress(
    val mac: String
) : BaseAddress {
    override fun toString(): String {
        return mac
    }
}
