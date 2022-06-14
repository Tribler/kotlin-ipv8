package nl.tudelft.ipv8.attestation.wallet.cryptography.bonehexact.attestations

import nl.tudelft.ipv8.attestation.wallet.cryptography.primitives.FP2Value
import nl.tudelft.ipv8.messaging.deserializeVarLen
import nl.tudelft.ipv8.messaging.serializeVarLen
import java.math.BigInteger

class BitPairAttestation(private val a: FP2Value, private val b: FP2Value, private val complement: FP2Value) {

    fun compress(): FP2Value {
        return this.a * this.b * this.complement
    }

    fun serialize(): ByteArray {
        return (
            serializeVarLen(this.a.a.toByteArray()) + serializeVarLen(this.a.b.toByteArray())
                + serializeVarLen(this.b.a.toByteArray()) + serializeVarLen(this.b.b.toByteArray())
                + serializeVarLen(this.complement.a.toByteArray()) + serializeVarLen(this.complement.b.toByteArray())
            )
    }

    companion object {
        fun deserialize(serialized: ByteArray, prime: BigInteger): BitPairAttestation {
            var localOffset = 0
            val numbers = arrayListOf<BigInteger>()
            while (serialized.isNotEmpty() && numbers.size < 6) {
                val unpacked = deserializeVarLen(serialized, localOffset)
                numbers.add(BigInteger(unpacked.first))
                localOffset += unpacked.second
            }
            return BitPairAttestation(FP2Value(prime, numbers[0], numbers[1]),
                FP2Value(prime, numbers[2], numbers[3]),
                FP2Value(prime, numbers[4], numbers[5]))
        }
    }
}
