package nl.tudelft.ipv8.attestation.wallet.cryptography.primitives

import nl.tudelft.ipv8.attestation.wallet.primitives.FP2Value
import nl.tudelft.ipv8.attestation.wallet.primitives.eSum
import nl.tudelft.ipv8.attestation.wallet.primitives.weilParing
import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigInteger


class TestEC {

    @Test
    fun smallWeilpairingTest() {
        /*
         * Check if Weil pairing in E[4] mod 11 of (5, 4) and (5x, 4) with S=(7, 6) equals 9 + 7x
         */
        val mod = BigInteger("11")
        val wp = weilParing(mod,
            BigInteger("4"),
            Pair(FP2Value(mod, BigInteger("5")), FP2Value(mod, BigInteger("4"))),
            Pair(FP2Value(mod, b = BigInteger("5")), FP2Value(mod, BigInteger("4"))),
            Pair(FP2Value(mod, BigInteger("7")), FP2Value(mod, BigInteger("6"))))

        assertEquals(wp.a, BigInteger("9"))
        assertEquals(wp.b, BigInteger("7"))
        assertEquals(wp.c, BigInteger.ZERO)
        assertEquals(wp.aC, BigInteger.ONE)
        assertEquals(wp.bC, BigInteger.ZERO)
        assertEquals(wp.cC, BigInteger.ZERO)
    }

    @Test
    fun mediumWeilpairingTest() {
        /*
         * Check if Weil pairing in E[408] mod 1223 of (764, 140) and (18x, 84) with S=(0, 1222) equals 438 + 50x
         */
        val mod = BigInteger("1223")
        val wp = weilParing(mod,
            BigInteger("408"),
            Pair(FP2Value(mod, BigInteger("764")), FP2Value(mod, BigInteger("140"))),
            Pair(FP2Value(mod, b = BigInteger("18")), FP2Value(mod, BigInteger("84"))),
            Pair(FP2Value(mod, BigInteger.ZERO), FP2Value(mod, BigInteger("1222"))))

        assertEquals(wp.a, BigInteger("438"))
        assertEquals(wp.b, BigInteger("50"))
        assertEquals(wp.c, BigInteger.ZERO)
        assertEquals(wp.aC, BigInteger.ONE)
        assertEquals(wp.bC, BigInteger.ZERO)
        assertEquals(wp.cC, BigInteger.ZERO)
    }

    @Test
    fun OobEsumTest() {
        /*
         * Check if EC sum of the point of infinity with it is the point at infinity.
         */
        assertEquals(eSum(BigInteger("11"), "O", "O"), "O")
    }

    @Test
    fun SpobEsumTest() {
        /*
         *Check if EC sum of the point of infinity with another point equals the other point.
         */
        val p = Pair(FP2Value(BigInteger("11"), BigInteger.ONE), FP2Value(BigInteger("11"), BigInteger("2")))

        assertEquals(eSum(BigInteger("11"), p, "O"), p)
        assertEquals(eSum(BigInteger("11"), "O", p), p)
    }
}
