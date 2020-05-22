package nl.tudelft.ipv8.messaging.payload

import nl.tudelft.ipv8.IPv4Address
import nl.tudelft.ipv8.util.toHex
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class IntroductionRequestPayloadTest {
    @Test
    fun serialize() {
        val payload = IntroductionRequestPayload(
            IPv4Address("1.2.3.4", 1234),
            IPv4Address("2.2.3.4", 2234),
            IPv4Address("3.2.3.4", 3234),
            true,
            ConnectionType.UNKNOWN,
            1
        )
        val serialized = payload.serialize()
        assertEquals("0102030404d20202030408ba030203040ca2010001", serialized.toHex())
    }

    @Test
    fun deserialize() {
        val destinationAddress = IPv4Address("1.2.3.4", 1234)
        val sourceLanAddress = IPv4Address("2.2.3.4", 2234)
        val sourceWanAddress = IPv4Address("3.2.3.4", 3234)
        val extraBytes = "hello".toByteArray(Charsets.US_ASCII)
        val payload = IntroductionRequestPayload(
            destinationAddress,
            sourceLanAddress,
            sourceWanAddress,
            true,
            ConnectionType.UNKNOWN,
            1,
            extraBytes
        )
        val serialized = payload.serialize()
        val (deserialized, size) = IntroductionRequestPayload.deserialize(serialized)
        assertEquals(destinationAddress, deserialized.destinationAddress)
        assertEquals(sourceLanAddress, deserialized.sourceLanAddress)
        assertEquals(sourceWanAddress, deserialized.sourceWanAddress)
        assertEquals(true, deserialized.advice)
        assertEquals(ConnectionType.UNKNOWN, deserialized.connectionType)
        assertEquals(1, deserialized.identifier)
        assertArrayEquals(extraBytes, deserialized.extraBytes)
        assertEquals(size, serialized.size)
    }
}
