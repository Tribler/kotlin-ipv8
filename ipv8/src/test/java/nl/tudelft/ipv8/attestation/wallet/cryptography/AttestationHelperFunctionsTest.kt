package nl.tudelft.ipv8.attestation.wallet.cryptography

import nl.tudelft.ipv8.attestation.wallet.cryptography.bonehexact.BonehPrivateKey
import nl.tudelft.ipv8.attestation.wallet.cryptography.bonehexact.decode
import nl.tudelft.ipv8.util.hexToBytes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigInteger

class AttestationHelperFunctionsTest {

    private val privateKey =
        BonehPrivateKey.deserialize("0000000907a016081ab53e90850000000900edf0f06d18de2c400000000906b15c56c9a13f1d250000000902fa4b47c93f63a2a3000000090412c4d01c5d61ce3b000000082426557bc0fc6af9000000044405f5d7".hexToBytes())!!


    @Test
    fun generateInverseGroupTest() {
        /*
         * Check if additive inverse group generation modulo (p + 1) is correct.
         */
        val p = 12253454.toBigInteger()
        val n = 20
        val group = generateModularAdditiveInverse(p, n)
        var sum = BigInteger.ZERO
        group.forEach { sum += it }

        assertEquals(20, group.size)
        assertEquals(BigInteger.ZERO, sum.mod(p + BigInteger.ONE))

    }

    @Test
    fun attestTest() {
        /*
         * Check if Attestations can be created correctly.
         */

        val attestations = arrayOf(
            attest(privateKey.publicKey(), BigInteger.ZERO, 2),
            attest(privateKey.publicKey(), BigInteger.ONE, 2),
            attest(privateKey.publicKey(), BigInteger("2"), 2),
            attest(privateKey.publicKey(), BigInteger.valueOf(3), 2),
        )

        val decoded = arrayOf(0, 1, 1, 2)
        for (i in attestations.indices) {
            assertEquals(decoded[i],
                decode(privateKey, arrayOf(0, 1, 2, 3), attestations[i].bitPairs.get(0).compress()))
        }

    }


    @Test
    fun emptyRelativityMapTest() {
        val relMap = createEmptyRelativityMap()

        assertTrue(relMap.size == 4)
        assertTrue(arrayListOf(0, 1, 2, 3).containsAll(relMap.keys.toList()))
        assertEquals(0, relMap.values.sum())
    }

    @Test
    fun binaryRelativityTest() {
        val values = arrayOf(
            Triple(hashMapOf(0 to 0, 1 to 1, 2 to 1, 3 to 0), 7, 4),
            Triple(hashMapOf(0 to 0, 1 to 1, 2 to 2, 3 to 0), 55, 6),
            Triple(hashMapOf(0 to 1, 1 to 1, 2 to 1, 3 to 0), 199, 6)
        )

        for ((expected, value, bitspace) in values) {
            assertEquals(expected, binaryRelativity(value.toBigInteger(), bitspace))
        }
    }

    @Test
    fun binaryRelativityMatchTest() {
        val a = hashMapOf(0 to 0, 1 to 1, 2 to 1, 3 to 0)
        val b = hashMapOf(0 to 0, 1 to 1, 2 to 2, 3 to 0)
        val c = hashMapOf(0 to 1, 1 to 1, 2 to 1, 3 to 0)

        assertTrue(0 < binaryRelativityMatch(b, a))
        assertEquals(0.0, binaryRelativityMatch(a, b), 0.0)

        assertTrue(0 < binaryRelativityMatch(c, a))
        assertEquals(0.0, binaryRelativityMatch(a, c), 0.0)

        assertEquals(0.0, binaryRelativityMatch(b, c), 0.0)
        assertEquals(0.0, binaryRelativityMatch(c, b), 0.0)
    }

    @Test
    fun binaryRelativityCertaintyTest() {
        val a = hashMapOf(0 to 0, 1 to 1, 2 to 1, 3 to 0)
        val b = hashMapOf(0 to 0, 1 to 1, 2 to 2, 3 to 0)

        assertEquals(0.375f, binaryRelativityCertainty(b, a).toFloat())
    }

    @Test
    fun createChallengeTest() {
        val pk = this.privateKey.publicKey()
        val challenges = arrayOf(
            createChallenge(pk, attest(pk, BigInteger.ZERO, 2).bitPairs[0]),
            createChallenge(pk, attest(pk, BigInteger.ONE, 2).bitPairs[0]),
            createChallenge(pk, attest(pk, BigInteger("2"), 2).bitPairs[0]),
            createChallenge(pk, attest(pk, BigInteger.valueOf(3), 2).bitPairs[0])
        )

        val values = arrayOf(0, 1, 1, 2)
        for (i in challenges.indices) {
            assertEquals(values[i], createChallengeResponse(this.privateKey, challenges[i]))
        }
    }

    @Test
    fun processChallengeResponseTest() {
        val a = hashMapOf(0 to 0, 1 to 1, 2 to 1, 3 to 0)
        val b = hashMapOf(0 to 0, 1 to 1, 2 to 2, 3 to 0)

        internalProcessChallengeResponse(a, 2)

        assertEquals(a, b)
    }
}
