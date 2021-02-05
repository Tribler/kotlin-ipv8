package nl.tudelft.ipv8.attestation.wallet.cryptography

import nl.tudelft.ipv8.attestation.wallet.cryptography.bonehexact.binaryRelativityMatch
import org.junit.Assert
import org.junit.Test

class AttestationHelperFunctionsTest {

    @Test
    fun binaryRelativityMatchTest() {
        val a = hashMapOf(0 to 0, 1 to 1, 2 to 1, 3 to 0)
        val b = hashMapOf(0 to 0, 1 to 1, 2 to 2, 3 to 0)
        val c = hashMapOf(0 to 1, 1 to 1, 2 to 1, 3 to 0)

        Assert.assertTrue(0 < binaryRelativityMatch(b, a))
        Assert.assertEquals(0F, binaryRelativityMatch(a, b))

        Assert.assertTrue(0 < binaryRelativityMatch(c, a))
        Assert.assertEquals(0F, binaryRelativityMatch(a, c))

        Assert.assertEquals(0F, binaryRelativityMatch(b, c))
        Assert.assertEquals(0F, binaryRelativityMatch(c, b))
    }
}
