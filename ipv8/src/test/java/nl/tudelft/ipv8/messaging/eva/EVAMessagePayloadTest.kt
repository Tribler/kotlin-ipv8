package nl.tudelft.ipv8.messaging.eva

import nl.tudelft.ipv8.Community
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class EVAMessagePayloadTest {
    @Test
    fun serialize_deserialize_write_request() {
        val dataSize = 49500
        val blockCount = 50
        val nonce = (0..EVAProtocol.MAX_NONCE).random().toULong()
        val id = "8ac5bc15a71f1e4189f6199fc573e7c7199ba81024f1c819311efeadd236870f"
        val info = Community.EVAId.EVA_PEERCHAT_ATTACHMENT
        val payload = EVAWriteRequestPayload(dataSize, blockCount, nonce, id, info)

        val serialized = payload.serialize()
        val (deserialized, size) = EVAWriteRequestPayload.deserialize(serialized)
        assertEquals(serialized.size, size)
        assertEquals(dataSize, deserialized.dataSize)
        assertEquals(blockCount, deserialized.blockCount)
        assertEquals(nonce, deserialized.nonce)
        assertEquals(id, deserialized.id)
        assertEquals(info, deserialized.info)
    }

    @Test
    fun serialize_deserialize_acknowledgement() {
        val number = 33
        val windowSize = 16
        val nonce = (0..EVAProtocol.MAX_NONCE).random().toULong()
        val payload = EVAAcknowledgementPayload(number, windowSize, nonce)

        val serialized = payload.serialize()
        val (deserialized, size) = EVAAcknowledgementPayload.deserialize(serialized)
        assertEquals(serialized.size, size)
        assertEquals(number, deserialized.number)
        assertEquals(windowSize, deserialized.windowSize)
        assertEquals(nonce, deserialized.nonce)
    }

    @Test
    fun serialize_deserialize_data() {
        val blockNumber = 33
        val nonce = (0..EVAProtocol.MAX_NONCE).random().toULong()
        val dataBinary = byteArrayOf(0x4c, 0x6f, 0x72, 0x65, 0x6d, 0x20, 0x69, 0x70, 0x73, 0x75, 0x6d, 0x20, 0x64, 0x6f, 0x6c, 0x6f, 0x72, 0x20, 0x73, 0x69, 0x74, 0x20, 0x61, 0x6d, 0x65, 0x74)
        val payload = EVADataPayload(blockNumber, nonce, dataBinary)

        val serialized = payload.serialize()
        val (deserialized, size) = EVADataPayload.deserialize(serialized)
        assertEquals(serialized.size, size)
        assertEquals(blockNumber, deserialized.blockNumber)
        assertEquals(nonce, deserialized.nonce)
        assertArrayEquals(dataBinary, deserialized.dataBinary)
    }

    @Test
    fun serialize_deserialize_error() {
        val message = "Current data size limit(1073741824) has been exceeded"
        val info = Community.EVAId.EVA_PEERCHAT_ATTACHMENT
        val payload = EVAErrorPayload(message, info)

        val serialized = payload.serialize()
        val (deserialized, size) = EVAErrorPayload.deserialize(serialized)
        assertEquals(serialized.size, size)
        assertEquals(message, deserialized.message)
        assertEquals(info, deserialized.info)
    }
}
