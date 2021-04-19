package nl.tudelft.ipv8

import nl.tudelft.ipv8.attestation.wallet.cryptography.bonehexact.BonehPrivateKey
import nl.tudelft.ipv8.keyvault.Key
import nl.tudelft.ipv8.messaging.Address
import nl.tudelft.ipv8.messaging.bluetooth.BluetoothAddress
import nl.tudelft.ipv8.util.toHex
import java.util.*
import kotlin.math.max

data class Peer(
    /**
     * The peer's key.
     */
    val key: Key,

    /**
     * The address of this peer it contacted us from (can be LAN or WAN).
     */
    var address: IPv4Address = IPv4Address.EMPTY,

    /**
     * The LAN address of this peer they believe they have.
     */
    var lanAddress: IPv4Address = IPv4Address.EMPTY,

    /**
     * The WAN address of this peer they believe they have.
     */
    var wanAddress: IPv4Address = IPv4Address.EMPTY,

    /**
     * The discovered Bluetooth address of a nearby peer.
     */
    var bluetoothAddress: BluetoothAddress? = null,

    /**
     * Is this peer suggested to us (otherwise it contacted us).
     */
    val intro: Boolean = true,

    /**
     * True if this peer signals support for TFTP.
     */
    var supportsTFTP: Boolean = false,

    /**
     * The private key for small Boneh/ Peng & Bao attestations.
     */
    var identityPrivateKeySmall: BonehPrivateKey? = null,

    /**
     * The private key for big Boneh attestations.
     */
    var identityPrivateKeyBig: BonehPrivateKey? = null,

    /**
     * The private key for huge Boneh attestations.
     */
    var identityPrivateKeyHuge: BonehPrivateKey? = null,

    ) {
    private var _lamportTimestamp = 0uL
    val lamportTimestamp: ULong
        get() = _lamportTimestamp

    val publicKey
        get() = key.pub()

    /**
     * Member ID.
     */
    val mid: String
        get() = key.keyToHash().toHex()

    /**
     * The timestamp of the last message sent to the peer.
     */
    var lastRequest: Date? = null

    /**
     * The timestamp of the last message received from the peer.
     */
    var lastResponse: Date? = if (intro) null else Date()

    /**
     * Durations of the last [MAX_PINGS] pings in seconds.
     */
    val pings = mutableListOf<Double>()

    /**
     * Update the Lamport timestamp for this peer. The Lamport clock dictates that the current timestamp is
     * the maximum of the last known and the most recently delivered timestamp. This is useful when messages
     * are delivered asynchronously.
     *
     * We also keep a real time timestamp of the last received message for timeout purposes.
     *
     * @param timestamp A received timestamp.
     */
    fun updateClock(timestamp: ULong) {
        _lamportTimestamp = max(_lamportTimestamp, timestamp)
        lastResponse = Date()
    }

    /**
     * Returns the average ping duration in seconds, calculated from the last [MAX_PINGS] pings.
     */
    fun getAveragePing(): Double {
        return pings.average()
    }

    /**
     * Adds a new ping duration.
     */
    fun addPing(ping: Double) {
        pings.add(ping)
        if (pings.size > MAX_PINGS) {
            pings.removeAt(0)
        }
    }

    fun isConnected(): Boolean {
        return !address.isEmpty() || bluetoothAddress != null
    }

    companion object {
        const val MAX_PINGS = 5

        fun createFromAddress(publicKey: Key, source: Address): Peer {
            return when (val address = source) {
                is IPv4Address -> Peer(publicKey, address = address)
                is BluetoothAddress -> Peer(publicKey, bluetoothAddress = address)
                else -> Peer(publicKey)
            }
        }
    }
}
