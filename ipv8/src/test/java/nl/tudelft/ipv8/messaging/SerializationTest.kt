package nl.tudelft.ipv8.messaging

import nl.tudelft.ipv8.util.toHex
import org.junit.Test

import org.junit.Assert.*

class SerializationTest {

    private enum class TestEnum {
        A, B, C
    }
    @Test
    fun simpleSerializeInt() {
        val value = 248375682
        val simple = simpleSerialize(value)
        val explicit = serializeInt(value)
        assertEquals(simple.toHex(), explicit.toHex())
    }

    @Test
    fun simpleSerializeLong() {
        val value = -3483756823489756836
        val simple = simpleSerialize(value)
        val explicit = serializeLong(value)
        assertEquals(simple.toHex(), explicit.toHex())
    }

    @Test
    fun simpleSerializeUInt() {
        val value = 248375682u
        val simple = simpleSerialize(value)
        val explicit = serializeUInt(value)
        assertEquals(simple.toHex(), explicit.toHex())
    }

    @Test
    fun simpleSerializeULong() {
        val value = 9483756823489756836u
        val simple = simpleSerialize(value)
        val explicit = serializeULong(value)
        assertEquals(simple.toHex(), explicit.toHex())
    }

    @Test
    fun simpleSerializeUShort() {
        val value = 1025.toUShort()
        val simple = simpleSerialize(value)
        val explicit = serializeUShort(value.toInt())
        assertEquals(simple.toHex(), explicit.toHex())
    }

    @Test
    fun simpleSerializeUByte() {
        val value = 248u.toUByte()
        val simple = simpleSerialize(value)
        val explicit = serializeUChar(value)
        assertEquals(simple.toHex(), explicit.toHex())
    }

    @Test
    fun simpleSerializeByteArray() {
        val value = byteArrayOf(0x01, 0x02, 0x03, 0x04)
        val simple = simpleSerialize(value)
        val explicit = serializeVarLen(value)
        assertEquals(simple.toHex(), explicit.toHex())
    }

    @Test
    fun simpleSerializeBoolean() {
        val value = true
        val simple = simpleSerialize(value)
        val explicit = serializeBool(value)
        assertEquals(simple.toHex(), explicit.toHex())
    }

    @Test
    fun simpleSerializeString() {
        val value = "Hello, World!"
        val simple = simpleSerialize(value)
        val explicit = serializeVarLen(value.toByteArray())
        assertEquals(simple.toHex(), explicit.toHex())
    }

    @Test
    fun simpleSerializeEnum() {
        val value = TestEnum.B
        val simple = simpleSerialize(value)
        val explicit = serializeUInt(value.ordinal.toUInt())
        assertEquals(simple.toHex(), explicit.toHex())
    }

    @Test
    fun simpleDeserializeString() {
        val value = "Hello, World!"
        val serialized = serializeVarLen(value.toByteArray())
        val (deserialized, _) = simpleDeserialize<String>(serialized)
        assertEquals(value, deserialized)
    }

    @Test
    fun simpleDeserializeEnum() {
        val value = TestEnum.B
        val serialized = simpleSerialize(value)
        val deserialized = simpleDeserialize<UInt>(serialized)
        assertEquals(value, TestEnum.entries[deserialized.first.toInt()])
    }

    @Test
    fun simpleDeserializeBoolean() {
        val value = true
        val serialized = simpleSerialize(value)
        val deserialized = simpleDeserialize<Boolean>(serialized)
        assertEquals(value, deserialized.first)
    }

    @Test
    fun simpleDeserializeByteArray() {
        val value = byteArrayOf(0x01, 0x02, 0x03, 0x04)
        val serialized = serializeVarLen(value)
        val (deserialized, _) = simpleDeserialize<ByteArray>(serialized)
        assertArrayEquals(value, deserialized)
    }

    @Test
    fun simpleDeserializeUByte() {
        val value = 248u.toUByte()
        val serialized = serializeUChar(value)
        val (deserialized, _) = simpleDeserialize<UByte>(serialized)
        assertEquals(value, deserialized)
    }

    @Test
    fun simpleDeserializeUShort() {
        val value = 1025.toUShort()
        val serialized = serializeUShort(value.toInt())
        val (deserialized, _) = simpleDeserialize<UShort>(serialized)
        assertEquals(value, deserialized)
    }

    @Test
    fun simpleDeserializeUInt() {
        val value = 248375682u
        val serialized = serializeUInt(value)
        val (deserialized, _) = simpleDeserialize<UInt>(serialized)
        assertEquals(value, deserialized)
    }

    @Test
    fun simpleDeserializeULong() {
        val value = 9483756823489756836u
        val serialized = serializeULong(value)
        val (deserialized, _) = simpleDeserialize<ULong>(serialized)
        assertEquals(value, deserialized)
    }

    @Test
    fun simpleDeserializeLong() {
        val value: Long = -3483756823489756836L
        val serialized = serializeLong(value)
        val (deserialized, _) = simpleDeserialize<Long>(serialized)
        assertEquals(value, deserialized)
    }

    @Test
    fun simpleDeserializeInt() {
        val value = 248375682
        val serialized = serializeInt(value)
        val (deserialized, _) = simpleDeserialize<Int>(serialized)
        assertEquals(value, deserialized)
    }

    @Test
    fun serializeBool_true() {
        val serialized = serializeBool(true)
        assertEquals("01", serialized.toHex())
    }

    @Test
    fun serializeBool_false() {
        val serialized = serializeBool(false)
        assertEquals("00", serialized.toHex())
    }

    @Test
    fun deserializeBool_true() {
        val serialized = serializeBool(true)
        assertEquals(true, deserializeBool(serialized))
    }

    @Test
    fun deserializeBool_false() {
        val serialized = serializeBool(false)
        assertEquals(false, deserializeBool(serialized))
    }

    @Test
    fun serializeUShort() {
        val serialized = serializeUShort(1025)
        assertEquals("0401", serialized.toHex())
    }

    @Test
    fun serializeUShort_max() {
        val uShort = UShort.MAX_VALUE
        val serialized = serializeUShort(uShort)
        assertEquals("ffff", serialized.toHex())
    }

    @Test
    fun deserializeRealUShort() {
        val uShort = UShort.MAX_VALUE
        val serialized = serializeUShort(uShort)
        assertEquals(uShort, deserializeRealUShort(serialized))
    }

    @Test
    fun deserializeUShort_simple() {
        val value = 1025
        val serialized = serializeUShort(value)
        assertEquals(value, deserializeUShort(serialized))
    }

    @Test
    fun deserializeUShort_negative() {
        val value = 389
        val serialized = serializeUShort(value)
        assertEquals(value, deserializeUShort(serialized))
    }

    @Test
    fun serializeULong_small() {
        val serialized = serializeULong(1uL)
        assertEquals("0000000000000001", serialized.toHex())
    }

    @Test
    fun serializeULong_max() {
        val serialized = serializeULong(18446744073709551615uL)
        assertEquals("ffffffffffffffff", serialized.toHex())
    }

    @Test
    fun serializeUInt() {
        val serialized = serializeUInt(UInt.MAX_VALUE)
        assertEquals("ffffffff", serialized.toHex())
    }
    @Test
    fun deserializeUInt_simple() {
        val value = 248375682u
        val serialized = serializeUInt(value)
        assertEquals(value, deserializeUInt(serialized))
    }

    @Test
    fun deserializeUInt_max() {
        val value = UInt.MAX_VALUE
        val serialized = serializeUInt(value)
        assertEquals(value, deserializeUInt(serialized))
    }

    @Test
    fun serializeInt() {
        val serialized = serializeInt(Int.MAX_VALUE)
        assertEquals("7fffffff", serialized.toHex())
    }

    @Test
    fun deserializeInt_simple() {
        val value = 248375682
        val serialized = serializeInt(value)
        assertEquals(value, deserializeInt(serialized))
    }

    @Test
    fun deserializeInt_max() {
        val value = Int.MAX_VALUE
        val serialized = serializeInt(value)
        assertEquals(value, deserializeInt(serialized))
    }

    @Test
    fun deserializeULong_test() {
        val value = 18446744073709551615uL
        val serialized = serializeULong(value)
        assertEquals(value, deserializeULong(serialized, 0))
    }

    @Test
    fun deserializeULong_test2() {
        val value = 1581459001000uL
        val serialized = serializeULong(value)
        assertEquals(value, deserializeULong(serialized, 0))
    }
}
