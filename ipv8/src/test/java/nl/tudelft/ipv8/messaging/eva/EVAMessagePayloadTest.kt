package nl.tudelft.ipv8.messaging.eva

import nl.tudelft.ipv8.Community
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class EVAMessagePayloadTest {
    @Test
    fun messagePayload_type() {
        val info = Community.EVAId.EVA_PEERCHAT_ATTACHMENT
        val id = "8ac5bc15a71f1e4189f6199fc573e7c7199ba81024f1c819311efeadd236870f"
        val nonce = (0..EVAProtocol.MAX_NONCE).random().toULong()
        val dataSize = 293123456.toULong()
        val blockCount = 50
        val blockSize = EVAProtocol.BLOCK_SIZE.toUInt()
        val windowSize = EVAProtocol.WINDOW_SIZE_IN_BLOCKS.toUInt()
        val payload = EVAWriteRequestPayload(info, id, nonce, dataSize, blockCount.toUInt(), blockSize, windowSize)

        assertEquals(Community.MessageId.EVA_WRITE_REQUEST, payload.type)
    }

    @Test
    fun serialize_deserialize_write_request() {
        val info = Community.EVAId.EVA_PEERCHAT_ATTACHMENT
        val id = "8ac5bc15a71f1e4189f6199fc573e7c7199ba81024f1c819311efeadd236870f"
        val nonce = (0..EVAProtocol.MAX_NONCE).random().toULong()
        val dataSize = 293123456.toULong()
        val blockCount = 50.toUInt()
        val blockSize = EVAProtocol.BLOCK_SIZE.toUInt()
        val windowSize = EVAProtocol.WINDOW_SIZE_IN_BLOCKS.toUInt()
        val payload = EVAWriteRequestPayload(info, id, nonce, dataSize, blockCount, blockSize, windowSize)

        val serialized = payload.serialize()
        val (deserialized, size) = EVAWriteRequestPayload.deserialize(serialized)
        assertEquals(serialized.size, size)
        assertEquals(info, deserialized.info)
        assertEquals(id, deserialized.id)
        assertEquals(nonce, deserialized.nonce)
        assertEquals(dataSize, deserialized.dataSize)
        assertEquals(blockCount, deserialized.blockCount)
    }

    @Test
    fun serialize_deserialize_acknowledgement() {
        val nonce = (0..EVAProtocol.MAX_NONCE).random().toULong()
        val ackWindow = 3.toUInt()
        val unAckedBlocks = "[1, 2]".encodeToByteArray()
        val payload = EVAAcknowledgementPayload(nonce, ackWindow, unAckedBlocks)

        val serialized = payload.serialize()
        val (deserialized, size) = EVAAcknowledgementPayload.deserialize(serialized)
        assertEquals(serialized.size, size)
        assertEquals(nonce, deserialized.nonce)
        assertEquals(ackWindow, deserialized.ackWindow)
        assertArrayEquals(unAckedBlocks, deserialized.unAckedBlocks)
    }

    @Test
    fun serialize_deserialize_data() {
        val blockNumber = 33.toUInt()
        val nonce = (0..EVAProtocol.MAX_NONCE).random().toULong()
        val data = byteArrayOf(0x4c, 0x6f, 0x72, 0x65, 0x6d, 0x20, 0x69, 0x70, 0x73, 0x75, 0x6d, 0x20, 0x64, 0x6f, 0x6c, 0x6f, 0x72, 0x20, 0x73, 0x69, 0x74, 0x20, 0x61, 0x6d, 0x65, 0x74)
        val payload = EVADataPayload(blockNumber, nonce, data)

        val serialized = payload.serialize()
        val (deserialized, size) = EVADataPayload.deserialize(serialized)
        assertEquals(serialized.size, size)
        assertEquals(blockNumber, deserialized.blockNumber)
        assertEquals(nonce, deserialized.nonce)
        assertArrayEquals(data, deserialized.data)
    }

    @Test
    fun serialize_deserialize_error() {
        val info = Community.EVAId.EVA_PEERCHAT_ATTACHMENT
        val message = "Current data size limit(1073741824) has been exceeded"
        val payload = EVAErrorPayload(info, message)

        val serialized = payload.serialize()
        val (deserialized, size) = EVAErrorPayload.deserialize(serialized)
        assertEquals(serialized.size, size)
        assertEquals(info, deserialized.info)
        assertEquals(message, deserialized.message)
    }
}
