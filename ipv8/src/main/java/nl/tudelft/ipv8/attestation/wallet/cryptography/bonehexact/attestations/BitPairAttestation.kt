package nl.tudelft.ipv8.attestation.wallet.cryptography.bonehexact.attestations

import nl.tudelft.ipv8.attestation.wallet.primitives.FP2Value
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
            val nums = arrayListOf<BigInteger>()
            while (serialized.isNotEmpty() && nums.size < 6) {
                val unpacked = deserializeVarLen(serialized, localOffset)
                nums.add(BigInteger(unpacked.first))
                localOffset += unpacked.second
            }
            return BitPairAttestation(FP2Value(prime, nums[0], nums[1]),
                FP2Value(prime, nums[2], nums[3]),
                FP2Value(prime, nums[4], nums[5]))
        }
    }
}
