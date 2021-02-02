package nl.tudelft.ipv8.attestation.wallet.cryptography.bonehexact

import nl.tudelft.ipv8.attestation.wallet.primitives.FP2Value
import nl.tudelft.ipv8.keyvault.PrivateKey
import nl.tudelft.ipv8.keyvault.PublicKey
import nl.tudelft.ipv8.messaging.deserializeVarLen
import nl.tudelft.ipv8.messaging.serializeVarLen
import java.math.BigInteger

const val PUBLIC_KEY_FIELDS = 5
const val PRIVATE_KEY_FIELDS = 7

open class BonehPublicKey(val p: BigInteger, val g: FP2Value, val h: FP2Value) : PublicKey {


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

            return BonehPublicKey(nums[0], FP2Value(nums[0], nums[1], nums[2]), FP2Value(nums[0], nums[3], nums[5]))
            // TODO Python implementation has unreachable code.
        }
    }

    open fun deserialize(serialized: ByteArray): BonehPublicKey? {
        return Companion.deserialize(serialized)
    }

    override fun verify(signature: ByteArray, msg: ByteArray): Boolean {
        TODO("Not yet implemented")
    }

    override fun getSignatureLength(): Int {
        TODO("Not yet implemented")
    }

    override fun encrypt(msg: ByteArray): ByteArray {
        TODO("Not yet implemented")
    }

    override fun keyToBin(): ByteArray {
        TODO("Not yet implemented")
    }
}

class BonehPrivateKey(p: BigInteger, g: FP2Value, h: FP2Value, val n: BigInteger, val t1: BigInteger) :
    BonehPublicKey(p, g, h), PrivateKey {


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
                val unpacked = deserializeVarLen(serialized, localOffset)
                nums.add(BigInteger(unpacked.first))
                localOffset += unpacked.second
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

    override fun sign(msg: ByteArray): ByteArray {
        TODO("Not yet implemented")
    }

    override fun decrypt(msg: ByteArray): ByteArray {
        TODO("Not yet implemented")
    }

    override fun pub(): PublicKey {
        TODO("Not yet implemented")
    }

    override fun keyToBin(): ByteArray {
        TODO("Not yet implemented")
    }


}
