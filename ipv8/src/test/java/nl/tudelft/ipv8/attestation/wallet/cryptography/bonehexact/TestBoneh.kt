package nl.tudelft.ipv8.attestation.wallet.cryptography.bonehexact

import nl.tudelft.ipv8.attestation.wallet.primitives.FP2Value
import nl.tudelft.ipv8.util.hexToBytes
import org.junit.Assert.*
import org.junit.Test
import java.math.BigInteger

class TestBoneh {

    val privateKey =
        BonehPrivateKey.deserialize("000000193f66b1832ebda2377020c49d4efb1f02d06abd620a3bb1cef90000001930a3f4173587f326a01065403d695ef74cd43541aef197021900000019249746a549c80dc37771fc52591733507ed0eca7ef21a58eed0000001926b4c4866545b883aa6eec92de3ae08ddcca251c52d67fed6a0000001939e634b0c3815508b1281e680247bd10652b9d7573cd011a9f00000018491c79ac07c5eb7e32e2e756d79e1f25d6f7a0badb5e34d30000000c60bcec8cf857fc02e4885091".hexToBytes())!!


    @Test
    fun generatePrimeTest() {
        /*
         * Check if the next prime (= l * n - 1 = 2 mod 3) after 10 is 29.
         */
        assertEquals(generatePrime(BigInteger("10")), BigInteger("29"))
    }

    @Test
    fun bilinearGroupTest() {
        /*
         * Check if a bilinear group can be created.
         */
        val x = bilinearGroup(BigInteger.valueOf(10),
            BigInteger.valueOf(29),
            BigInteger.valueOf(4),
            BigInteger.valueOf(5),
            BigInteger.valueOf(4),
            BigInteger.valueOf(5))
        val y = FP2Value(
            BigInteger.valueOf(29), BigInteger.valueOf(19), BigInteger.valueOf(5))
        assertEquals(x, y)
    }

    @Test
    fun bilinearGroupTorsionPointTest() {
        /*
         * Check if a bilinear group returns 0 if there is no possible pairing.
         */
        assertEquals(bilinearGroup(BigInteger.valueOf(10),
            BigInteger.valueOf(29),
            BigInteger.valueOf(2),
            BigInteger.valueOf(3),
            BigInteger.valueOf(2),
            BigInteger.valueOf(3)), FP2Value(BigInteger.valueOf(29)))
    }

    @Test
    fun isGoodWeilPairingTest() {
        /*
         * Check if is_good_wp returns True for 26 + 17x with n = 10
         */
        assertTrue(isGoodWp(BigInteger.valueOf(10),
            FP2Value(BigInteger.valueOf(29), BigInteger.valueOf(26), BigInteger.valueOf(17))))
    }

    @Test
    fun test_is_bad_weil_pairing() {
        /*
         * Check if is_good_wp returns False for 0, 1 and x with n = 10
         */
        assertFalse(isGoodWp(BigInteger.valueOf(10), FP2Value(BigInteger.valueOf(29))))
        assertFalse(isGoodWp(BigInteger.valueOf(10), FP2Value(BigInteger.valueOf(29), BigInteger.valueOf(1))))
        assertFalse(isGoodWp(BigInteger.valueOf(10), FP2Value(BigInteger.valueOf(29), b = BigInteger.valueOf(1))))
    }

    @Test
    fun getGoodWeilPairingTest() {
        /*
         * Check if get_good_wp returns a proper Weil pairing for n = 10, p = 29.
         */
        val wp = getGoodWp(BigInteger.valueOf(10), BigInteger.valueOf(29)).second
        assertTrue(isGoodWp(BigInteger.valueOf(10), wp))
    }

    @Test
    fun encodingRandomTest() {
        /*
         * Check if the same value is encoded with a random masking.
         */
        val pk = this.privateKey.publicKey()
        assertNotSame(encode(pk, BigInteger.ZERO), encode(pk, BigInteger.ZERO))
    }

    @Test
    fun decodingSameTest() {
        /*
         * Check if values are encoded with a random masking.
         */
        val pk = this.privateKey.publicKey()
        val a = encode(pk, BigInteger.ZERO)
        val b = encode(pk, BigInteger.ZERO)

        assertEquals(decode(this.privateKey, arrayOf(0), a), decode(this.privateKey, arrayOf(0), b))
    }

    @Test
    fun decodingAfterHomomorphicAddTest() {
        /*
         * Check if values can still be decrypted after a homomorphic add.
         */
        val pk = privateKey.publicKey()
        val a = encode(pk, BigInteger.ONE)
        val b = encode(pk, BigInteger("3"))

        val x = decode(this.privateKey, arrayOf(4), a * b)
        assertEquals(4, x)
    }

    @Test
    fun decodingOutOfSpaceTest() {
        /*
         * Check if decode return None if the message is outside of the allowed space.
         */
        val pk = privateKey.publicKey()

        assertNull(decode(this.privateKey, arrayOf(0), encode(pk, BigInteger.ONE)))
    }

    @Test
    fun generateKeypairTest() {
        /*
         * Check if we can create a new keypair.
         */
        val (pk, sk) = generateKeypair(32)

        assertEquals(pk.p, sk.p)
        assertEquals(pk.g, sk.g)
        assertEquals(pk.h, sk.h)
        assertEquals(decode(sk, arrayOf(0, 1, 2), encode(pk, BigInteger.ZERO)), 0)
        assertEquals(decode(sk, arrayOf(0, 1, 2), encode(pk, BigInteger.ONE)), 1)
        assertEquals(decode(sk, arrayOf(0, 1, 2), encode(pk, BigInteger("2"))), 2)
    }
}
