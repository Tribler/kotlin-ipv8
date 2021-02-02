package nl.tudelft.ipv8.attestation.wallet.primitives

import java.math.BigInteger

fun weilParing(
    mod: BigInteger,
    m: BigInteger,
    p: Pair<FP2Value, FP2Value>,
    q: Pair<FP2Value, FP2Value>,
    s: Pair<FP2Value, FP2Value>,
): FP2Value {
    val nS = Pair(s.first, FP2Value(mod, -BigInteger.ONE) * s.second)
    val a = millerCalc(mod, m, p, eSum(mod, q, s) as Pair<FP2Value, FP2Value>)
    val b = millerCalc(mod, m, p, s)
    val c = millerCalc(mod, m, q, eSum(mod, p, nS) as Pair<FP2Value, FP2Value>)
    val d = millerCalc(mod, m, q, nS)
    val wp = (a * d) / (b * c)
    return wp.wpNominator() * wp.wpDenomInverse()
}

fun millerCalc(mod: BigInteger, m: BigInteger, p: Pair<FP2Value, FP2Value>, r: Pair<FP2Value, FP2Value>): FP2Value {
    val mList = m.toString(2).toList().reversed().map { it.toInt() }
    var t = p
    var f = FP2Value(mod, BigInteger.ONE)

    // -2 as we do not want to include size - 1
    for (i in (mList.size - 2) downTo 0) {
        f = (f * f * h(mod, t, t, r.first, r.second)).normalize()
        t = eSum(mod, t, t) as Pair<FP2Value, FP2Value>
        if (mList[i] == 1) {
            f = (f * h(mod, t, p, r.first, r.second)).normalize()
            t = eSum(mod, t, p) as Pair<FP2Value, FP2Value>
        }
    }
    return f
}

fun h(mod: BigInteger, p: Pair<FP2Value, FP2Value>, q: Pair<FP2Value, FP2Value>, x: FP2Value, y: FP2Value): FP2Value {
    val (x1, y1) = p
    val (x2, y2) = q

    val l: FP2Value
    if (x1 == x2 && y1 == FP2Value(mod, -BigInteger.ONE) * y2)
        return (x - x1).normalize()
    if (x1 == x2 && y1 == y2) {
        l = (FP2Value(mod, BigInteger("3")) * x1 * x1) / (FP2Value(mod, BigInteger.TWO) * y1)
        return ((y - y1 - l * (x - x1)) / (x + (x1 + x2) - l * l)).normalize()
    }
    l = (y2 - y1) / (x2 - x1)
    return ((y - y1 - l * (x - x1)) / (x + (x1 + x2) - l * l)).normalize()
}


fun eSum(mod: BigInteger, p: Any, q: Any): Any {
    if (p == "O" && q == "O")
        return "O"
    if (p == "O")
        return q
    if (q == "O")
        return p

    // TODO String input
    val (x1, y1) = (p as Pair<FP2Value, FP2Value>)
    val (x2, y2) = (q as Pair<FP2Value, FP2Value>)

    if (x1 == x2 && y1 == FP2Value(mod, -BigInteger.ONE) * y2)
        return "O"

    var l = if (x1 == x2) {
        ((FP2Value(mod, BigInteger("3")) * x1 * x1) / ((FP2Value(mod,
            BigInteger.TWO) * y1).normalize()))
    } else {
        ((y1 - y2) / (x1 - x2)).normalize()
    }

    val x3 = l * l - x1 - x2
    val y3 = l * (x3 - x1) + y1

    return Pair(x3.normalize(), (FP2Value(mod, -BigInteger.ONE) * y3).normalize())
}
