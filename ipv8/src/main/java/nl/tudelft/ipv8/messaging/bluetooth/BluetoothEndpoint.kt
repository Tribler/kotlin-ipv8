package nl.tudelft.ipv8.messaging.bluetooth

import nl.tudelft.ipv8.messaging.Endpoint

/**
 * A base Bluetooth endpoint that is currently implemented only on Android by [BluetoothLeEndpoint].
 * In the future, it could be implemented for JVM as well using javax.bluetooth APIs, or for iOS with
 * Kotlin Multiplatform.
 */
abstract class BluetoothEndpoint : Endpoint<BluetoothAddress>() {
    /**
     * Attempts to connect to the provided Bluetooth address.
     */
    abstract fun connectTo(address: BluetoothAddress)
}
