package nl.tudelft.ipv8.messaging.payload

import nl.tudelft.ipv8.messaging.payload.TransferRequestPayload.TransferStatus.*
import nl.tudelft.ipv8.messaging.payload.TransferRequestPayload.TransferType.*
import nl.tudelft.ipv8.messaging.serializeInt
import nl.tudelft.ipv8.messaging.serializeLong
import nl.tudelft.ipv8.messaging.serializeUInt
import nl.tudelft.ipv8.messaging.serializeVarLen
import nl.tudelft.ipv8.util.toHex
import org.junit.Assert.assertEquals
import org.junit.Test

class TransferRequestPayloadTest {

    @Test
    fun testSerialize() {
        val payload = TransferRequestPayload("test1.txt", REQUEST, FILE, 1024)
        val serialized = payload.serialize()

        // Manual serialization
        val filename = serializeVarLen(payload.filename.toByteArray())
        val status = serializeUInt(REQUEST.ordinal.toUInt())
        val type = serializeUInt(FILE.ordinal.toUInt())
        val dataSize = serializeInt(payload.dataSize)

        assertEquals((filename + status + type + dataSize).toHex(), serialized.toHex())
    }

    @Test
    fun testSerializeEmpty() {
        val payload = TransferRequestPayload("test2.txt", REQUEST, FILE)
        val serialized = payload.serialize()

        // Manual serialization
        val filename = serializeVarLen(payload.filename.toByteArray())
        val status = serializeUInt(payload.status.ordinal.toUInt())
        val type = serializeUInt(payload.type.ordinal.toUInt())
        val dataSize = serializeInt(payload.dataSize)

        assertEquals(serialized.toHex(), (filename + status + type + dataSize).toHex())
    }

    @Test
    fun testDeserialize() {
        val payload = TransferRequestPayload("test3.txt", DECLINE, RANDOM_DATA, 1024)

        val serialized = payload.serialize()
        val deserialized = TransferRequestPayload.Deserializer.deserialize(serialized, 0).first
        assertEquals(payload, deserialized)
    }

    @Test
    fun testDeserializeEmpty() {
        val payload = TransferRequestPayload("test4.txt", REQUEST, FILE)

        val serialized = payload.serialize()
        val deserialized = TransferRequestPayload.Deserializer.deserialize(serialized, 0).first
        assertEquals(payload, deserialized)
    }

}
