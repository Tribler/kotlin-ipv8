package nl.tudelft.ipv8.attestation.schema

import nl.tudelft.ipv8.attestation.IdentityAlgorithm
import nl.tudelft.ipv8.attestation.WalletAttestation
import nl.tudelft.ipv8.attestation.wallet.cryptography.bonehexact.BonehExactAlgorithm
import nl.tudelft.ipv8.attestation.wallet.cryptography.bonehexact.BonehPrivateKey
import nl.tudelft.ipv8.attestation.wallet.cryptography.bonehexact.attestations.BonehAttestation
import nl.tudelft.ipv8.keyvault.PrivateKey

const val CRYPTO_BASE_PACKAGE = "nl.tudelft.ipv8.attestation.wallet.cryptography."

class SchemaManager {

    private val formats = HashMap<String, HashMap<String, Any>>()

    fun deserialize(serialized: ByteArray, idFormat: String): WalletAttestation {
        return when (val algorithmName = getAlgorithmName(idFormat)) {
            "bonehexact" -> {
                BonehAttestation.deserialize(serialized, idFormat)
            }
            "pengbaorange" -> {
                TODO("Not yet implemented.")
            }
            "irmaexact" -> {
                TODO("Not yet implemented.")
            }
            else -> {
                throw RuntimeException("Attempted to load unknown proof algorithm: ${algorithmName}.")
            }
        }
    }

    fun deserializePrivate(privateKey: BonehPrivateKey, serialized: ByteArray, idFormat: String): WalletAttestation {
        return when (val algorithmName = getAlgorithmName(idFormat)) {
            "bonehexact" -> {
                BonehAttestation.deserializePrivate(privateKey, serialized, idFormat)
            }
            "pengbaorange" -> {
                TODO("Not yet implemented.")
            }
            "irmaexact" -> {
                TODO("Not yet implemented.")
            }
            else -> {
                throw RuntimeException("Attempted to load unknown proof algorithm: ${algorithmName}.")
            }
        }
    }

    fun registerSchema(
        schemaName: String,
        algorithmName: String,
        parameters: HashMap<String, Any>,
    ) {
        val schema = HashMap<String, Any>()
        schema["algorithm"] = algorithmName
        schema.putAll(parameters)
        this.formats[schemaName] = schema
    }

    fun registerDefaultSchemas() {
        TODO()
    }

    fun getAlgorithmInstance(idFormat: String): IdentityAlgorithm {
        return when (val algorithmName = getAlgorithmName(idFormat)) {
            "bonehexact" -> {
                BonehExactAlgorithm(idFormat, this.formats)
            }
            "pengbaorange" -> {
                TODO("Not yet implemented.")
            }
            "irmaexact" -> {
                TODO("Not yet implemented.")
            }
            else -> {
                throw RuntimeException("Attempted to load unknown proof algorithm: ${algorithmName}.")
            }
        }
    }

    fun getAlgorithmName(idFormat: String): String {
        return this.formats[idFormat]?.get("algorithm").toString()
    }

}
