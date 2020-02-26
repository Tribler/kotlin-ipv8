package nl.tudelft.ipv8.messaging.bluetooth

interface BluetoothConnection {
    fun send(data: ByteArray)
}
