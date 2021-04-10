package nl.tudelft.ipv8.attestation.wallet.cryptography.pengbaorange

import nl.tudelft.ipv8.attestation.wallet.cryptography.bonehexact.BonehPublicKey
import nl.tudelft.ipv8.attestation.wallet.primitives.FP2Value
import nl.tudelft.ipv8.util.hexToBytes
import org.junit.Assert.*
import org.junit.Test
import java.math.BigInteger

class TestBoudot {

    // Test params to be equal to the PyIPV8 tests.
    val pk = BonehPublicKey(
        p = BigInteger("1855855294356961364177"),
        g = FP2Value(mod = BigInteger("1855855294356961364177"),
            a = BigInteger("1640955591379995824691"),
            b = BigInteger("322057703443002024702")),
        h = FP2Value(mod = BigInteger("1855855294356961364177"),
            a = BigInteger("706495420229569786251"),
            b = BigInteger("156305917664511526783"))
    )

    val bitspace = 32
    val a = 0.toBigInteger()
    val b = 18.toBigInteger()
    val r = 2.toBigInteger()
    val ra = 3.toBigInteger()


    // Commitment generated with above parameters for message `7`
    val el =
        EL.deserialize("020000002101c42b8cb90f9de7b77897d0df644410cf8581a7381392562164b5e0da43d3e47f00000021012d725dd0b513efcfa5ba8b3f982d608a59011a25626138c43fb537a3faf168ff000000210710ae32e43e779edde25f437d9110433e16069ce04e488d0c6c242fba6c50be7c000000210096b92ee85a89f7e7d2dd459fcc16b0452c808d12b130b66bb3adaea4de5c0fb6".hexToBytes()).first

    val sqr =
        SQR.deserialize("00000009649b2a7d7992b008d1000000092ef4b2b3890136f39d00000009300f2cb5896dcadc49000000a7010000002a0a16027dee86ae81c4cfc7f529f46261608252ab4daa517d68e21720790e4199f78a1929dbd013a105b60000002a0285809f7ba1aba0714188435457007cbe08eaaf87506267fdb8c0ad262dfb6c15ca968e206a9777949e0000002102af10fec9115c85e85cd7d7aef94a65d3cc6c7357642706e8a37bb0ecd804845b0000002100abc43fb24457217a1735f5ebbe529974f31b1cd5d909c1ba28deec3b36011fa3".hexToBytes()).first

    @Test
    fun ELSerializationTest() {
        /*
         * Check if Boudot equality checks are correctly serialized.
         */
        val el = EL(BigInteger("68174117739048401651990398043836840253231911962332258219191049320869958258614"),
            BigInteger("818089412868580819823884776526042083038782943547987098630292591850439499103868"),
            BigInteger("-136348235478096803303980796087673680506463823924664516192465696382710419908863"),
            BigInteger("204522353217145204955971194131510520759695735886996774897791958142625032430719"))

        assertEquals(this.el, el)
    }

    @Test
    fun ELEqualTest() {
        /*
         * Check if the Boudot commitment equality holds.
         */
        val m = 7.toBigInteger()  // Message

        // Commitment
        val c = this.pk.g.bigIntPow(m) * this.pk.h.bigIntPow(this.r)
        val c1 = c / (this.pk.g.bigIntPow(this.a - BigInteger.ONE))
        val c2 = this.pk.g.bigIntPow(this.b + BigInteger.ONE) / c

        // Shadow commitment
        val ca = c1.bigIntPow(this.b - m + BigInteger.ONE) * this.pk.h.bigIntPow(this.ra)

        // Check
        assertTrue(this.el.check(this.pk.g, this.pk.h, c1, this.pk.h, c2, ca))
    }

    @Test
    fun ELModifyCommitmentTest() {
        /*
         *Check if the Boudot commitment equality fails if the commitment message changes.
         */
        val m = 7.toBigInteger() // Message
        val fake = 8.toBigInteger()

        // Commitment
        val c = this.pk.g.bigIntPow(fake) * this.pk.h.bigIntPow(this.r)
        val c1 = c / (this.pk.g.bigIntPow(this.a - BigInteger.ONE))
        val c2 = this.pk.g.bigIntPow(this.b + BigInteger.ONE) / c

        // Shadow commitment
        val ca = c1.bigIntPow(this.b - m + BigInteger.ONE) * this.pk.h.bigIntPow(this.ra)

        // Check
        assertFalse(this.el.check(this.pk.g, this.pk.h, c1, this.pk.h, c2, ca))
    }

    @Test
    fun ELModifyShadowCommitmentTest() {
        /*
         * Check if the Boudot commitment equality fails if the shadow commitment message changes.
         */
        val m = 7.toBigInteger()  // Message
        val fake = 8.toBigInteger()

        // Commitment
        val c = this.pk.g.bigIntPow(m) * this.pk.h.bigIntPow(this.r)
        val c1 = c / (this.pk.g.bigIntPow(this.a - BigInteger.ONE))
        val c2 = this.pk.g.bigIntPow(this.b + BigInteger.ONE) / c

        // Shadow commitment
        val ca = c1.bigIntPow(this.b - fake + BigInteger.ONE) * this.pk.h.bigIntPow(this.ra)

        // Check
        assertFalse(this.el.check(this.pk.g, this.pk.h, c1, this.pk.h, c2, ca))
    }

    @Test
    fun ELModifyCommitmentsTest() {
        /*
         * Check if the Boudot commitment equality fails if the shadow+commitment messages change.
         */
        val fake = 8.toBigInteger()

        // Commitment
        val c = this.pk.g.bigIntPow(fake) * this.pk.h.bigIntPow(this.r)
        val c1 = c / (this.pk.g.bigIntPow(this.a - BigInteger.ONE))
        val c2 = this.pk.g.bigIntPow(this.b + BigInteger.ONE) / c

        // Shadow commitment
        val ca = c1.bigIntPow(this.b - fake + BigInteger.ONE) * this.pk.h.bigIntPow(this.ra)

        // Check
        assertFalse(this.el.check(this.pk.g, this.pk.h, c1, this.pk.h, c2, ca))
    }

    @Test
    fun SQRTest() {
        /*
         * Check if the Boudot commitment-is-square holds.
         */
        val sqr = SQR.create(4.toBigInteger(), 81.toBigInteger(), this.pk.g, this.pk.h, this.b.toInt(), this.bitspace)
        assertTrue(sqr.check(this.pk.g,
            this.pk.h,
            this.pk.g.bigIntPow(16.toBigInteger()) * this.pk.h.bigIntPow(81.toBigInteger())))
    }

    @Test
    fun SQRModifyCommitmentTest() {
        /*
         * Check if the Boudot commitment-is-square fails if the commitment message changes.
         */
        val sqr = SQR.create(9.toBigInteger(), 81.toBigInteger(), this.pk.g, this.pk.h, this.b.toInt(), this.bitspace)
        assertFalse(sqr.check(this.pk.g,
            this.pk.h,
            this.pk.g.bigIntPow(16.toBigInteger()) * this.pk.h.bigIntPow(81.toBigInteger())))

    }

    @Test
    fun SQRModifyShadowCommitmentTest() {
        /*
         * Check if the Boudot commitment-is-square fails if the shadow commitment message changes.
         */
        val sqr = SQR.create(4.toBigInteger(), 81.toBigInteger(), this.pk.g, this.pk.h, this.b.toInt(), this.bitspace)
        assertFalse(sqr.check(this.pk.g,
            this.pk.h,
            this.pk.g.bigIntPow(9.toBigInteger()) * this.pk.h.bigIntPow(81.toBigInteger())))
    }

    @Test
    fun SQRSerializationTest() {
        /*
         * Check if Boudot commitment-is-square checks are correctly serialized.
         */
        val el = EL(BigInteger("77692238748522549651683873494184958254240308467748145113388211342290056191907"),
            BigInteger("310768954994090198606735493976739833016961233870992580453552845369160224769115"),
            BigInteger("1378784829646587776157777333351527905785206201874845030390922605077647796646962410317344630260602014"),
            BigInteger("-5515139318586351104624816262067481296619038413746351139945096955324703586833572052593411867046643126"))
        val sqr =
            SQR(FP2Value(this.pk.g.mod, BigInteger("866182580282760557469"), BigInteger("886537163949459823689")), el)

        assertEquals(this.sqr, sqr)
    }
}
