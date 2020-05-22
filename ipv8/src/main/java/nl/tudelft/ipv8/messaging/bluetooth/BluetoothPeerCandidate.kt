package nl.tudelft.ipv8.messaging.bluetooth

data class BluetoothPeerCandidate(
    val mid: String?,
    val address: BluetoothAddress,
    val txPowerLevel: Int?,
    val rssi: Int
)
