package nl.tudelft.ipv8.attestation.wallet.primitives


import java.math.BigInteger


class FP2Value(
    val mod: BigInteger,
    a: BigInteger = BigInteger.ZERO,
    b: BigInteger = BigInteger.ZERO,
    c: BigInteger = BigInteger.ZERO,
    aC: BigInteger = BigInteger.ONE,
    bC: BigInteger = BigInteger.ZERO,
    cC: BigInteger = BigInteger.ZERO,
) {
    var a = a % mod
    var b = b % mod
    var c = c % mod
    var aC = aC % mod
    var bC = bC % mod
    var cC = cC % mod


    operator fun times(other: FP2Value): FP2Value {
        if (this.mod != other.mod)
            throw AssertionError("Modulo must be equal!")

        val a = (this.aC * other.a - this.cC * other.a + this.a * other.aC - this.c * other.aC - this.bC * other.b
            + this.cC * other.b - this.aC * other.c + this.bC * other.c - this.a * other.cC + this.b * other.cC)
        val b = (this.bC * other.a - this.cC * other.a + this.b * other.aC - this.c * other.aC + this.aC * other.b
            - this.bC * other.b + this.a * other.bC - this.b * other.bC - this.aC * other.c + this.cC * other.c
            - this.a * other.cC + this.c * other.cC)
        val aC = (this.aC * other.aC - this.cC * other.aC - this.bC * other.bC
            + this.cC * other.bC - this.aC * other.cC + this.bC * other.cC)
        val bC = (this.bC * other.aC - this.cC * other.aC + this.aC * other.bC
            - this.bC * other.bC - this.aC * other.cC + this.cC * other.cC)

        return FP2Value(this.mod, a = a, b = b, aC = aC, bC = bC)
    }

    fun bigIntPow(e: BigInteger): FP2Value {
        var n = if (e < BigInteger.ZERO) -e else e
        var r = FP2Value(this.mod, BigInteger.ONE)
        var u = this
        while (n > BigInteger.ZERO) {
            if (n % BigInteger("2") == BigInteger.ONE)
                r *= u
            u *= u
            n /= BigInteger("2")
        }
        return if (e < BigInteger.ZERO) r.inverse().normalize() else r
    }

    fun normalize(): FP2Value {
        // TODO: Verify that this is correct
        val mp = (this.aC % this.mod).modInverse(this.mod)
        if (mp > BigInteger.ZERO) {
            val a = (this.a * mp) % this.mod
            val b = (this.b * mp) % this.mod
            val c = (this.c * mp) % this.mod
            val aC = BigInteger.ONE
            val bC = (this.bC * mp) % this.mod
            val cC = (this.cC * mp) % this.mod
            return FP2Value(this.mod, a, b, c, aC, bC, cC)
        }
        return FP2Value(this.mod, this.a, this.b, this.c, this.aC, this.bC, this.cC)
    }

    fun inverse(): FP2Value {
        return FP2Value(this.mod, a = this.aC, b = this.bC, c = this.cC, aC = this.a, bC = this.b, cC = this.c)
    }

    fun wpNominator(): FP2Value {
        return FP2Value(this.mod, this.a, this.b)
    }

    fun wpDenomInverse(): FP2Value {
        val iq = FP2Value(this.mod, this.aC * this.aC - this.aC * this.bC + this.bC * this.bC)
        val a = FP2Value(this.mod, this.aC - this.bC) // iq
        val b = FP2Value(this.mod, -this.bC) // iq
        return FP2Value(this.mod, a.normalize().a, b.normalize().a)
    }

    operator fun div(other: FP2Value): FP2Value {
        assert(this.mod == other.mod)

        val a = (this.a * other.aC - this.c * other.aC - this.b * other.bC
            + this.c * other.bC - this.a * other.cC + this.b * other.cC)
        val b = (this.b * other.aC - this.c * other.aC + this.a * other.bC
            - this.b * other.bC - this.a * other.cC + this.c * other.cC)
        val aC = (this.aC * other.a - this.cC * other.a - this.bC * other.b
            + this.cC * other.b - this.aC * other.c + this.bC * other.c)
        val bC = (this.bC * other.a - this.cC * other.a + this.aC * other.b
            - this.bC * other.b - this.aC * other.c + this.cC * other.c)

        return FP2Value(this.mod, a = a, b = b, aC = aC, bC = bC)
    }

    operator fun minus(other: FP2Value): FP2Value {
        assert(this.mod == other.mod)

        val a = (-this.aC * other.a + this.cC * other.a + this.a * other.aC - this.c * other.aC + this.bC * other.b
            - this.cC * other.b - this.b * other.bC + this.c * other.bC + this.aC * other.c - this.bC * other.c
            - this.a * other.cC + this.b * other.cC)
        val b = (-this.bC * other.a + this.cC * other.a + this.b * other.aC - this.c * other.aC - this.aC * other.b
            + this.bC * other.b + this.a * other.bC - this.b * other.bC + this.aC * other.c - this.cC * other.c
            - this.a * other.cC + this.c * other.cC)
        val aC = (this.aC * other.aC - this.cC * other.aC - this.bC * other.bC
            + this.cC * other.bC - this.aC * other.cC + this.bC * other.cC)
        val bC = (this.bC * other.aC - this.cC * other.aC + this.aC * other.bC
            - this.bC * other.bC - this.aC * other.cC + this.cC * other.cC)

        return FP2Value(this.mod, a = a, b = b, aC = aC, bC = bC)
    }

    operator fun plus(other: FP2Value): FP2Value {
        assert(this.mod == other.mod)

        val a = (this.aC * other.a - this.cC * other.a + this.a * other.aC - this.c * other.aC - this.bC * other.b
            + this.cC * other.b - this.aC * other.c + this.bC * other.c - this.a * other.cC + this.b * other.cC)
        val b = (this.bC * other.a - this.cC * other.a + this.b * other.aC - this.c * other.aC + this.aC * other.b
            - this.bC * other.b + this.a * other.bC - this.b * other.bC - this.aC * other.c + this.cC * other.c
            - this.a * other.cC + this.c * other.cC)
        val aC = (this.aC * other.aC - this.cC * other.aC - this.bC * other.bC
            + this.cC * other.bC - this.aC * other.cC + this.bC * other.cC)
        val bC = (this.bC * other.aC - this.cC * other.aC + this.aC * other.bC
            - this.bC * other.bC - this.aC * other.cC + this.cC * other.cC)
        return FP2Value(this.mod, a = a, b = b, aC = aC, bC = bC)
    }

}
