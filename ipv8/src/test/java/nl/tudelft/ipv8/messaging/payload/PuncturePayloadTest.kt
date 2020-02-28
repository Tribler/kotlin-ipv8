package nl.tudelft.ipv8.messaging.payload

import nl.tudelft.ipv8.IPv4Address
import org.junit.Assert.assertEquals
import org.junit.Test

class PuncturePayloadTest {
    @Test
    fun serialize_deserialize() {
        val sourceLanAddress = IPv4Address("1.2.3.4", 1234)
        val sourceWanAddress = IPv4Address("2.2.3.4", 2234)
        val identifier = 1
        val payload = PuncturePayload(
            sourceLanAddress,
            sourceWanAddress,
            identifier
        )
        val serialized = payload.serialize()
        val (deserialized, size) = PuncturePayload.deserialize(serialized)
        assertEquals(serialized.size, size)
        assertEquals(sourceLanAddress, deserialized.sourceLanAddress)
        assertEquals(sourceWanAddress, deserialized.sourceWanAddress)
        assertEquals(identifier, deserialized.identifier)
    }
}
