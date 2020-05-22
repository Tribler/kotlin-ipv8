package nl.tudelft.ipv8.messaging.payload

import nl.tudelft.ipv8.IPv4Address
import nl.tudelft.ipv8.messaging.*

/**
 * The payload for an introduction-request message.
 */
data class IntroductionRequestPayload(
    /**
     * The address of the receiver. Effectively this should be the wan address that others can
     * use to contact the receiver.
     */
    val destinationAddress: IPv4Address,

    /**
     * The lan address of the sender. Nodes in the same LAN should use this address to communicate.
     */
    val sourceLanAddress: IPv4Address,

    /**
     * The wan address of the sender. Nodes not in the same LAN should use this address
     * to communicate.
     */
    val sourceWanAddress: IPv4Address,

    /**
     * When True the receiver will introduce the sender to a new node.  This introduction will be
     * facilitated by the receiver sending a puncture-request to the new node.
     */
    val advice: Boolean,

    /**
     * Indicating the connection type that the message creator has.
     */
    val connectionType: ConnectionType,

    /**
     * A number that must be given in the associated introduction-response. This number allows to
     * distinguish between multiple introduction-response messages.
     */
    val identifier: Int,

    /**
     * Can be used to piggyback extra information.
     */
    val extraBytes: ByteArray = byteArrayOf()
) : Serializable {
    override fun serialize(): ByteArray {
        return destinationAddress.serialize() +
                sourceLanAddress.serialize() +
                sourceWanAddress.serialize() +
                createConnectionByte(connectionType, advice) +
                serializeUShort(identifier) +
                extraBytes
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as IntroductionRequestPayload

        if (destinationAddress != other.destinationAddress) return false
        if (sourceLanAddress != other.sourceLanAddress) return false
        if (sourceWanAddress != other.sourceWanAddress) return false
        if (advice != other.advice) return false
        if (connectionType != other.connectionType) return false
        if (identifier != other.identifier) return false
        if (!extraBytes.contentEquals(other.extraBytes)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = destinationAddress.hashCode()
        result = 31 * result + sourceLanAddress.hashCode()
        result = 31 * result + sourceWanAddress.hashCode()
        result = 31 * result + advice.hashCode()
        result = 31 * result + connectionType.hashCode()
        result = 31 * result + identifier
        result = 31 * result + extraBytes.contentHashCode()
        return result
    }

    companion object Deserializer : Deserializable<IntroductionRequestPayload> {
        override fun deserialize(buffer: ByteArray, offset: Int): Pair<IntroductionRequestPayload, Int> {
            var localOffset = 0
            val (destinationAddress, _) = IPv4Address.deserialize(buffer, offset + localOffset)
            localOffset += IPv4Address.SERIALIZED_SIZE
            val (sourceLanAddress, _) = IPv4Address.deserialize(buffer, offset + localOffset)
            localOffset += IPv4Address.SERIALIZED_SIZE
            val (sourceWanAddress, _) = IPv4Address.deserialize(buffer, offset + localOffset)
            localOffset += IPv4Address.SERIALIZED_SIZE
            val (advice, connectionType) = deserializeConnectionByte(buffer[offset + localOffset])
            localOffset++
            val identifier = deserializeUShort(buffer, offset + localOffset)
            localOffset += SERIALIZED_USHORT_SIZE
            val (extraBytes, extraBytesLen) = deserializeRaw(buffer, offset + localOffset)
            localOffset += extraBytesLen
            val payload = IntroductionRequestPayload(
                destinationAddress,
                sourceLanAddress,
                sourceWanAddress,
                advice,
                connectionType,
                identifier,
                extraBytes
            )
            return Pair(payload, localOffset)
        }
    }
}
