package nl.tudelft.ipv8.messaging.payload

import nl.tudelft.ipv8.messaging.Deserializable
import nl.tudelft.ipv8.messaging.Serializable

class UtpHeartbeatPayload(

) : Serializable {
    override fun serialize(): ByteArray {
        TODO("Not yet implemented")
    }

    companion object Deserializer : Deserializable<UtpHeartbeatPayload> {
        override fun deserialize(buffer: ByteArray, offset: Int): Pair<UtpHeartbeatPayload, Int> {
            TODO("Not yet implemented")
        }
    }
}
