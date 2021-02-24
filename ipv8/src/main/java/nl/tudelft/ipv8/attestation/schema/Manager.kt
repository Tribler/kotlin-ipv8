package nl.tudelft.ipv8.attestation.schema

import nl.tudelft.ipv8.attestation.IdentityAlgorithm
import nl.tudelft.ipv8.attestation.WalletAttestation
import nl.tudelft.ipv8.attestation.wallet.cryptography.bonehexact.BonehExactAlgorithm
import nl.tudelft.ipv8.attestation.wallet.cryptography.bonehexact.BonehPrivateKey
import nl.tudelft.ipv8.attestation.wallet.cryptography.bonehexact.attestations.BonehAttestation
import nl.tudelft.ipv8.attestation.wallet.cryptography.pengbaorange.PengBaoAttestation
import nl.tudelft.ipv8.attestation.wallet.cryptography.pengbaorange.Pengbaorange

class AlgorithmScheme(
    val schemaName: String,
    val algorithmName: String,
    val keySize: Int,
    val hashAlgorithm: String? = null,
    val min: Int? = null,
    val max: Int? = null,
)

class SchemaManager {

    private val formats = HashMap<String, HashMap<String, Any>>()

    fun deserialize(serialized: ByteArray, idFormat: String): WalletAttestation {
        return when (val algorithmName = getAlgorithmName(idFormat)) {
            "bonehexact" -> {
                BonehAttestation.deserialize(serialized, idFormat)
            }
            "pengbaorange" -> {
                PengBaoAttestation.deserialize(serialized, idFormat)
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
                PengBaoAttestation.deserializePrivate(privateKey, serialized, idFormat)
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

    // TODO: Read in default schemas.
    fun registerDefaultSchemas() {
        val defaultSchemas = arrayListOf<AlgorithmScheme>()
        defaultSchemas.add(AlgorithmScheme("id_metadata", "bonehexact", 32, "sha256_4"))
        defaultSchemas.add(AlgorithmScheme("id_metadata_big", "bonehexact", 64, "sha256"))
        defaultSchemas.add(AlgorithmScheme("id_metadata_huge", "bonehexact", 96, "sha512"))
        defaultSchemas.add(AlgorithmScheme("id_metadata_range_18plus", "pengbaorange", 32, min = 18, max = 200))
        defaultSchemas.add(AlgorithmScheme("id_metadata_range_underage", "pengbaorange", 32, min = 0, max = 17))

        defaultSchemas.forEach {
            val params = hashMapOf<String, Any>("key_size" to it.keySize)
            if (it.hashAlgorithm != null) {
                params["hash"] = it.hashAlgorithm
            }
            if (it.min != null && it.max != null) {
                params["min"] = it.min
                params["max"] = it.max
            }

            this.registerSchema(it.schemaName,
                it.algorithmName,
                params
            )
        }
    }

    fun getAlgorithmInstance(idFormat: String): IdentityAlgorithm {
        return when (val algorithmName = getAlgorithmName(idFormat)) {
            "bonehexact" -> {
                BonehExactAlgorithm(idFormat, this.formats)
            }
            "pengbaorange" -> {
                Pengbaorange(idFormat, this.formats)
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
