package nl.tudelft.ipv8.messaging.payload

import nl.tudelft.ipv8.IPv4Address
import nl.tudelft.ipv8.util.toHex
import org.junit.Assert
import org.junit.Test

class PayloadUtilsTest {
    @Test
    fun connectionType() {
        val connectionType = ConnectionType.PUBLIC
        val payload = IntroductionRequestPayload(
            IPv4Address("1.2.3.4", 1234),
            IPv4Address("2.2.3.4", 2234),
            IPv4Address("3.2.3.4", 3234),
            true,
            connectionType,
            1
        )
        val serialized = payload.serialize()
        Assert.assertEquals("0102030404d20202030408ba030203040ca2810001", serialized.toHex())
        val (deserialized, size) = IntroductionRequestPayload.deserialize(serialized)
        Assert.assertEquals(connectionType, deserialized.connectionType)
        Assert.assertEquals(size, serialized.size)
    }
}
