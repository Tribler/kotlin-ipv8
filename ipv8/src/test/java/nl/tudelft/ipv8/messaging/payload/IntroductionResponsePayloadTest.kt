package nl.tudelft.ipv8.messaging.payload

import nl.tudelft.ipv8.IPv4Address
import nl.tudelft.ipv8.util.toHex
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class IntroductionResponsePayloadTest {
    @Test
    fun serialize() {
        val payload = IntroductionResponsePayload(
            IPv4Address("1.2.3.4", 1234),
            IPv4Address("2.2.3.4", 2234),
            IPv4Address("3.2.3.4", 3234),
            IPv4Address("4.2.3.4", 4234),
            IPv4Address("5.2.3.4", 5234),
            ConnectionType.UNKNOWN,
            false,
            2
        )
        val serialized = payload.serialize()
        assertEquals("0102030404d20202030408ba030203040ca204020304108a050203041472000002", serialized.toHex())
    }

    @Test
    fun deserialize() {
        val destinationAddress = IPv4Address("1.2.3.4", 1234)
        val sourceLanAddress = IPv4Address("2.2.3.4", 2234)
        val sourceWanAddress = IPv4Address("3.2.3.4", 3234)
        val lanIntroductionAddress = IPv4Address("4.2.3.4", 4234)
        val wanIntroductionAddress = IPv4Address("5.2.3.4", 5234)
        val identifier = 2
        val extraBytes = "hello".toByteArray(Charsets.US_ASCII)
        val payload = IntroductionResponsePayload(
            destinationAddress,
            sourceLanAddress,
            sourceWanAddress,
            lanIntroductionAddress,
            wanIntroductionAddress,
            ConnectionType.UNKNOWN,
            false,
            identifier,
            extraBytes
        )
        val serialized = payload.serialize()
        val (deserialized, size) = IntroductionResponsePayload.deserialize(serialized)
        assertEquals(destinationAddress, deserialized.destinationAddress)
        assertEquals(sourceLanAddress, deserialized.sourceLanAddress)
        assertEquals(sourceWanAddress, deserialized.sourceWanAddress)
        assertEquals(lanIntroductionAddress, deserialized.lanIntroductionAddress)
        assertEquals(wanIntroductionAddress, deserialized.wanIntroductionAddress)
        assertEquals(false, deserialized.tunnel)
        assertEquals(ConnectionType.UNKNOWN, deserialized.connectionType)
        assertEquals(identifier, deserialized.identifier)
        assertArrayEquals(extraBytes, deserialized.extraBytes)
        assertEquals(size, serialized.size)
    }
}
