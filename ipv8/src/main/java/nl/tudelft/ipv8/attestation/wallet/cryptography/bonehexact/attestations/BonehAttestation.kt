package nl.tudelft.ipv8.attestation.wallet.cryptography.bonehexact.attestations

import nl.tudelft.ipv8.attestation.WalletAttestation
import nl.tudelft.ipv8.attestation.wallet.cryptography.bonehexact.BonehPrivateKey
import nl.tudelft.ipv8.attestation.wallet.cryptography.bonehexact.BonehPublicKey
import nl.tudelft.ipv8.keyvault.PrivateKey
import nl.tudelft.ipv8.keyvault.PublicKey

class BonehAttestation(
    override val publicKey: BonehPublicKey,
    val bitpairs: ArrayList<BitPairAttestation>,
    override val idFormat: String? = null,
) : WalletAttestation() {

    override fun serialize(): ByteArray {
        val out = byteArrayOf()
        this.bitpairs.forEach { out.plus(it.serialize()) }
        return this.publicKey.serialize() + out
    }

    override fun deserialize(serialized: ByteArray, idFormat: String): WalletAttestation {
        return BonehAttestation.deserialize(serialized, idFormat)
    }

    companion object {
        fun deserialize(serialized: ByteArray, idFormat: String): BonehAttestation {
            val publicKey = BonehPublicKey.deserialize(serialized)
            val bitPairs = arrayListOf<BitPairAttestation>()
            var rem = serialized.copyOfRange(publicKey!!.serialize().size, serialized.size)
            while (rem.isNotEmpty()) {
                val attest = BitPairAttestation.deserialize(rem, publicKey.p)
                bitPairs.add(attest)
                rem = rem.copyOfRange(attest.serialize().size, rem.size)
            }
            return BonehAttestation(publicKey, bitPairs, idFormat)
        }

        fun deserializePrivate(
            privateKey: BonehPrivateKey,
            serialized: ByteArray,
            idFormat: String,
        ): WalletAttestation {
            return this.deserialize(serialized, idFormat)
        }
    }

    override fun serializePrivate(publicKey: BonehPublicKey): ByteArray {
        return this.serialize()
    }

    override fun deserializePrivate(
        privateKey: BonehPrivateKey,
        serialized: ByteArray,
        idFormat: String,
    ): WalletAttestation {
        return BonehAttestation.deserializePrivate(privateKey, serialized, idFormat)
    }
}
