package nl.tudelft.ipv8.attestation.wallet.cryptography.bonehexact

import nl.tudelft.ipv8.attestation.schema.SchemaManager
import nl.tudelft.ipv8.util.toHex
import org.junit.Assert.assertEquals
import org.junit.Test

class TestKeys {

    @Test
    fun keySerializationTest() {
        val schema = SchemaManager()
        schema.registerDefaultSchemas()
        val alg = schema.getAlgorithmInstance("id_metadata")
        val key = alg.generateSecretKey()
        val keySerialized = key.serialize()
        val keyHex = keySerialized.toHex()

        val keyRecov = BonehPrivateKey.deserialize(keySerialized)!!
        val keyRecovSerialized = keyRecov.serialize()
        val keyRecovHex = keyRecovSerialized.toHex()

        assertEquals(keyRecovHex, keyHex)
        assertEquals(key, keyRecov)
    }

}
