package nl.tudelft.ipv8.messaging.eva

import nl.tudelft.ipv8.Community
import org.junit.Assert.*
import org.junit.Test

class EVAMessagePayloadTest {
    fun createAcknowledgementPayload(): EVAAcknowledgementPayload {
        val nonce = 1412311.toULong()
        val ackWindow = 3.toUInt()
        val unAckedBlocks = "[1, 2]".encodeToByteArray()
        return EVAAcknowledgementPayload(nonce, ackWindow, unAckedBlocks)
    }

    fun createDataPayload(): EVADataPayload {
        val blockNumber = 33.toUInt()
        val nonce = 1234567.toULong()
        val data = byteArrayOf(0x4c, 0x6f, 0x72, 0x65, 0x6d, 0x20, 0x69, 0x70, 0x73, 0x75, 0x6d, 0x20, 0x64, 0x6f, 0x6c, 0x6f, 0x72, 0x20, 0x73, 0x69, 0x74, 0x20, 0x61, 0x6d, 0x65, 0x74)
        return EVADataPayload(blockNumber, nonce, data)
    }

    @Test
    fun messagePayload_type() {
        val info = Community.EVAId.EVA_PEERCHAT_ATTACHMENT
        val id = "8ac5bc15a71f1e4189f6199fc573e7c7199ba81024f1c819311efeadd236870f"
        val nonce = (0..EVAProtocol.MAX_NONCE).random().toULong()
        val dataSize = 293123456.toULong()
        val blockCount = 50
        val blockSize = EVAProtocol.BLOCK_SIZE.toUInt()
        val windowSize = EVAProtocol.WINDOW_SIZE.toUInt()
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
        val windowSize = EVAProtocol.WINDOW_SIZE.toUInt()
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
    fun ack_payload_equals() {
        val ackPayload = createAcknowledgementPayload()
        val dataPayload = createDataPayload()

        val ackPayloadDifferentNonce = ackPayload.copy(nonce = 7654321.toULong())
        val ackPayloadDifferentAckWindow = ackPayload.copy(ackWindow = 2.toUInt())
        val ackPayloadDifferentUnackedBlocks = ackPayload.copy(unAckedBlocks = "[1, 2, 3]".encodeToByteArray())

        assertEquals(true, ackPayload.equals(ackPayload))
        assertEquals(ackPayload::class.java, ackPayloadDifferentNonce::class.java)
        assertNotEquals(ackPayload::class.java, dataPayload::class.java)
        assertEquals(false, ackPayload.equals(dataPayload))
        assertEquals(false, ackPayload.equals(ackPayloadDifferentNonce))
        assertEquals(false, ackPayload.equals(ackPayloadDifferentAckWindow))
        assertEquals(false, ackPayload.equals(ackPayloadDifferentUnackedBlocks))
    }

    @Test
    fun ack_payload_hashcode() {
        val ackPayload = createAcknowledgementPayload()
        val ackPayloadCopy = createAcknowledgementPayload()
        val ackPayloadOther = ackPayload.copy(nonce = 987.toULong())

        assertEquals(true, ackPayload.equals(ackPayloadCopy) && ackPayloadCopy.equals(ackPayload))
        assertEquals(ackPayload.hashCode(), ackPayloadCopy.hashCode())

        assertEquals(false, ackPayload.equals(ackPayloadOther) && ackPayloadOther.equals(ackPayload))
        assertNotEquals(ackPayload.hashCode(), ackPayloadOther.hashCode())
    }

    @Test
    fun serialize_deserialize_acknowledgement() {
        val payload = createAcknowledgementPayload()

        val serialized = payload.serialize()
        val (deserialized, size) = EVAAcknowledgementPayload.deserialize(serialized)
        assertEquals(serialized.size, size)
        assertEquals(payload.nonce, deserialized.nonce)
        assertEquals(payload.ackWindow, deserialized.ackWindow)
        assertArrayEquals(payload.unAckedBlocks, deserialized.unAckedBlocks)
    }

    @Test
    fun serialize_deserialize_data() {
        val payload = createDataPayload()

        val serialized = payload.serialize()
        val (deserialized, size) = EVADataPayload.deserialize(serialized)
        assertEquals(serialized.size, size)
        assertEquals(payload.blockNumber, deserialized.blockNumber)
        assertEquals(payload.nonce, deserialized.nonce)
        assertArrayEquals(payload.data, deserialized.data)
    }

    @Test
    fun data_payload_equals() {
        val dataPayload = createDataPayload()
        val ackPayload = createAcknowledgementPayload()
        val dataPayloadDifferentBlockNumber = dataPayload.copy(blockNumber = 123.toUInt())
        val dataPayloadDifferentNonce = dataPayload.copy(nonce = 7654321.toULong())
        val dataPayloadDifferentData = dataPayload.copy(data = byteArrayOf(91, 0, 1, 2, 3, 4, 93))

        assertEquals(true, dataPayload.equals(dataPayload))
        assertEquals(dataPayload::class.java, dataPayloadDifferentBlockNumber::class.java)
        assertNotEquals(dataPayload::class.java, ackPayload::class.java)
        assertEquals(false, dataPayload.equals(ackPayload))
        assertEquals(false, dataPayload.equals(dataPayloadDifferentBlockNumber))
        assertEquals(false, dataPayload.equals(dataPayloadDifferentNonce))
        assertEquals(false, dataPayload.equals(dataPayloadDifferentData))
    }

    @Test
    fun data_payload_hashcode() {
        val dataPayload = createDataPayload()
        val dataPayloadCopy = createDataPayload()
        val dataPayloadOther = dataPayload.copy(blockNumber = 12345.toUInt())

        assertEquals(true, dataPayload.equals(dataPayloadCopy) && dataPayloadCopy.equals(dataPayload))
        assertEquals(dataPayload.hashCode(), dataPayloadCopy.hashCode())

        assertEquals(false, dataPayload.equals(dataPayloadOther) && dataPayloadOther.equals(dataPayload))
        assertNotEquals(dataPayload.hashCode(), dataPayloadOther.hashCode())
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
