package nl.tudelft.ipv8.messaging.payload

import nl.tudelft.ipv8.IPv4Address
import nl.tudelft.ipv8.messaging.*

/**
 * The payload for an introduction-response message.
 *
 * When the associated request wanted advice the sender will also sent a puncture-request
 * message to either the lan_introduction_address or the wan_introduction_address
 * (depending on their positions).  The introduced node must sent a puncture message to the
 * receiver to punch a hole in its NAT.
 */
data class IntroductionResponsePayload(
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
     * The wan address of the sender. Nodes not in the same LAN should use this address to
     * communicate.
     */
    val sourceWanAddress: IPv4Address,

    /**
     * The lan address of the node that the sender advises the receiver to contact. This address
     * is zero when the associated request did not want advice.
     */
    val lanIntroductionAddress: IPv4Address,

    /**
     * The wan address of the node that the sender advises the receiver to contact. This address
     * is zero when the associated request did not want advice.
     */
    val wanIntroductionAddress: IPv4Address,

    /**
     * A unicode string indicating the connection type that the introduced node has. Currently the
     * following values are supported: u"unknown", u"public", and u"symmetric-NAT".
     */
    val connectionType: ConnectionType,

    /**
     * A boolean indicating that the connection is tunneled and all messages send to the introduced
     * candidate require a ffffffff prefix.
     */
    val tunnel: Boolean,

    /**
     * A number that was given in the associated introduction-request.  This number allows to
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
                lanIntroductionAddress.serialize() +
                wanIntroductionAddress.serialize() +
                createConnectionByte(connectionType) +
                serializeUShort(identifier) +
                extraBytes
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as IntroductionResponsePayload

        if (destinationAddress != other.destinationAddress) return false
        if (sourceLanAddress != other.sourceLanAddress) return false
        if (sourceWanAddress != other.sourceWanAddress) return false
        if (lanIntroductionAddress != other.lanIntroductionAddress) return false
        if (wanIntroductionAddress != other.wanIntroductionAddress) return false
        if (connectionType != other.connectionType) return false
        if (tunnel != other.tunnel) return false
        if (identifier != other.identifier) return false
        if (!extraBytes.contentEquals(other.extraBytes)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = destinationAddress.hashCode()
        result = 31 * result + sourceLanAddress.hashCode()
        result = 31 * result + sourceWanAddress.hashCode()
        result = 31 * result + lanIntroductionAddress.hashCode()
        result = 31 * result + wanIntroductionAddress.hashCode()
        result = 31 * result + connectionType.hashCode()
        result = 31 * result + tunnel.hashCode()
        result = 31 * result + identifier
        result = 31 * result + extraBytes.contentHashCode()
        return result
    }

    companion object Deserializer : Deserializable<IntroductionResponsePayload> {
        override fun deserialize(buffer: ByteArray, offset: Int): Pair<IntroductionResponsePayload, Int> {
            var localOffset = 0
            val (destinationAddress, _) = IPv4Address.deserialize(buffer, offset + localOffset)
            localOffset += IPv4Address.SERIALIZED_SIZE
            val (sourceLanAddress, _) = IPv4Address.deserialize(buffer, offset + localOffset)
            localOffset += IPv4Address.SERIALIZED_SIZE
            val (sourceWanAddress, _) = IPv4Address.deserialize(buffer, offset + localOffset)
            localOffset += IPv4Address.SERIALIZED_SIZE
            val (lanIntroductionAddress, _) = IPv4Address.deserialize(buffer, offset + localOffset)
            localOffset += IPv4Address.SERIALIZED_SIZE
            val (wanIntroductionAddress, _) = IPv4Address.deserialize(buffer, offset + localOffset)
            localOffset += IPv4Address.SERIALIZED_SIZE
            val (_, connectionType) = deserializeConnectionByte(buffer[offset + localOffset])
            localOffset++
            val identifier = deserializeUShort(buffer, offset + localOffset)
            localOffset += SERIALIZED_USHORT_SIZE
            val (extraBytes, extraBytesLen) = deserializeRaw(buffer, offset + localOffset)
            localOffset += extraBytesLen
            val payload = IntroductionResponsePayload(
                destinationAddress,
                sourceLanAddress,
                sourceWanAddress,
                lanIntroductionAddress,
                wanIntroductionAddress,
                connectionType,
                false,
                identifier,
                extraBytes
            )
            return Pair(payload, localOffset)
        }
    }
}
