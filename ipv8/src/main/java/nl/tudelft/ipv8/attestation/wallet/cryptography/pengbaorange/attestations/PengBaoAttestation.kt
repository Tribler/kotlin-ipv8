package nl.tudelft.ipv8.attestation.wallet.cryptography.pengbaorange.attestations

import nl.tudelft.ipv8.attestation.wallet.cryptography.WalletAttestation
import nl.tudelft.ipv8.attestation.wallet.cryptography.bonehexact.BonehPrivateKey
import nl.tudelft.ipv8.attestation.wallet.cryptography.bonehexact.BonehPublicKey
import nl.tudelft.ipv8.attestation.wallet.cryptography.pengbaorange.commitments.PengBaoPrivateData
import nl.tudelft.ipv8.attestation.wallet.cryptography.pengbaorange.commitments.PengBaoPublicData

class PengBaoAttestation(
    val publicData: PengBaoPublicData,
    val privateData: PengBaoPrivateData?,
    override val idFormat: String? = null,
) : WalletAttestation() {

    override val publicKey = publicData.publicKey

    override fun serialize(): ByteArray {
        return this.publicData.serialize()
    }

    override fun deserialize(serialized: ByteArray, idFormat: String): WalletAttestation {
        return PengBaoAttestation.deserialize(serialized, idFormat)
    }

    override fun serializePrivate(publicKey: BonehPublicKey): ByteArray {
        return if (this.privateData != null) {
            val publicData = this.publicData.serialize()
            val privateData = this.privateData.encode(publicKey)
            publicData + privateData
        } else {
            throw RuntimeException("Private data was null.")
        }
    }

    override fun deserializePrivate(
        privateKey: BonehPrivateKey,
        serialized: ByteArray,
        idFormat: String?,
    ): WalletAttestation {
        return PengBaoAttestation.deserializePrivate(privateKey, serialized, idFormat)
    }

    companion object {
        fun deserialize(serialized: ByteArray, idFormat: String? = null): PengBaoAttestation {
            val (pubicData, _) = PengBaoPublicData.deserialize(serialized)
            return PengBaoAttestation(pubicData, null, idFormat)
        }

        fun deserializePrivate(
            privateKey: BonehPrivateKey,
            serialized: ByteArray,
            idFormat: String? = null,
        ): PengBaoAttestation {
            val (publicData, rem) = PengBaoPublicData.deserialize(serialized)
            val privateData = PengBaoPrivateData.decode(privateKey, rem)

            return PengBaoAttestation(publicData, privateData, idFormat)
        }
    }
}
