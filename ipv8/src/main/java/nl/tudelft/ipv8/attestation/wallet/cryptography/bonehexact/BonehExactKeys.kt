package nl.tudelft.ipv8.attestation.wallet.cryptography.bonehexact

import nl.tudelft.ipv8.attestation.wallet.primitives.FP2Value
import nl.tudelft.ipv8.keyvault.PrivateKey
import nl.tudelft.ipv8.keyvault.PublicKey
import nl.tudelft.ipv8.messaging.SERIALIZED_UINT_SIZE
import nl.tudelft.ipv8.messaging.deserializeUInt
import nl.tudelft.ipv8.messaging.deserializeVarLen
import nl.tudelft.ipv8.messaging.serializeVarLen
import java.math.BigInteger

const val PUBLIC_KEY_FIELDS = 5
const val PRIVATE_KEY_FIELDS = 7

open class BonehPublicKey(val p: BigInteger, val g: FP2Value, val h: FP2Value) {


    open fun serialize(): ByteArray {
        return serializeVarLen(this.p.toByteArray()) + serializeVarLen(this.g.a.toByteArray()) +
            serializeVarLen(this.g.b.toByteArray()) + serializeVarLen(this.h.a.toByteArray()) +
            serializeVarLen(this.h.b.toByteArray())
    }

    companion object {
        fun deserialize(serialized: ByteArray): BonehPublicKey? {
            var localOffset = 0
            val nums = arrayListOf<BigInteger>()
            while (serialized.isNotEmpty() && nums.size < PUBLIC_KEY_FIELDS) {
                val unpacked = deserializeVarLen(serialized, localOffset)
                nums.add(BigInteger(unpacked.first))
                localOffset += unpacked.second
            }
            if (nums.size < PUBLIC_KEY_FIELDS) {
                return null
            }

            return BonehPublicKey(nums[0], FP2Value(nums[0], nums[1], nums[2]), FP2Value(nums[0], nums[3], nums[4]))
            // TODO Python implementation has unreachable code.
        }
    }

    open fun deserialize(serialized: ByteArray): BonehPublicKey? {
        return Companion.deserialize(serialized)
    }

}

class BonehPrivateKey(p: BigInteger, g: FP2Value, h: FP2Value, val n: BigInteger, val t1: BigInteger) :
    BonehPublicKey(p, g, h) {


    override fun serialize(): ByteArray {
        return super.serialize() + serializeVarLen(this.n.toByteArray()) + serializeVarLen(this.t1.toByteArray())
    }

    fun publicKey(): BonehPublicKey {
        return BonehPublicKey(this.p, this.g, this.h)
    }

    override fun deserialize(serialized: ByteArray): BonehPrivateKey? {
        return BonehPrivateKey.deserialize(serialized)
    }

    companion object {
        fun deserialize(serialized: ByteArray): BonehPrivateKey? {
            var localOffset = 0
            val nums = arrayListOf<BigInteger>()
            while (serialized.isNotEmpty() && nums.size < PRIVATE_KEY_FIELDS) {
                val unpacked = deserializeUInt(serialized, localOffset)
                nums.add(BigInteger(unpacked.toString()))
                localOffset += SERIALIZED_UINT_SIZE
            }
            if (nums.size < PRIVATE_KEY_FIELDS) {
                return null
            }

            return BonehPrivateKey(nums[0],
                FP2Value(nums[0], nums[1], nums[2]),
                FP2Value(nums[0], nums[3], nums[5]),
                nums[5],
                nums[6])
        }
    }

}
