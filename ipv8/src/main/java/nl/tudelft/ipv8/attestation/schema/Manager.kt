package nl.tudelft.ipv8.attestation.schema

import nl.tudelft.ipv8.attestation.IdentityAlgorithm
import nl.tudelft.ipv8.attestation.WalletAttestation
import nl.tudelft.ipv8.attestation.wallet.cryptography.bonehexact.BonehExactAlgorithm
import nl.tudelft.ipv8.attestation.wallet.cryptography.bonehexact.BonehPrivateKey
import nl.tudelft.ipv8.attestation.wallet.cryptography.bonehexact.attestations.BonehAttestation

class AlgorithmScheme(
    val schemaName: String,
    val algorithmName: String,
    val keySize: Int,
    val hashAlgorithm: String,
)

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

    fun deserializePrivate(
        privateKey: BonehPrivateKey,
        serialized: ByteArray,
        idFormat: String,
    ): WalletAttestation {
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
        val defaultSchemas = arrayListOf<AlgorithmScheme>()
        defaultSchemas.add(AlgorithmScheme("id_metadata", "bonehexact", 32, "sha256_4"))
        defaultSchemas.add(AlgorithmScheme("id_metadata_big", "bonehexact", 64, "sha256"))
        defaultSchemas.add(AlgorithmScheme("id_metadata_huge", "bonehexact", 96, "sha512"))

        defaultSchemas.forEach {
            this.registerSchema(it.schemaName,
                it.algorithmName,
                hashMapOf("key_size" to it.keySize, "hash" to it.hashAlgorithm))
        }
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
