package nl.tudelft.ipv8.attestation.schema

import nl.tudelft.ipv8.attestation.IdentityAlgorithm
import nl.tudelft.ipv8.attestation.WalletAttestation
import nl.tudelft.ipv8.keyvault.PrivateKey

class SchemaManager {

    private val formats = HashMap<String, HashMap<String, Any>>()
    private val algorithms = HashMap<String, Class<IdentityAlgorithm>>()


    fun getAlgorithmClass(algorithmName: String): Class<IdentityAlgorithm> {
        if (algorithmName in this.algorithms.keys) {
            return this.algorithms[algorithmName]!!
        }

        lateinit var algorithm: IdentityAlgorithm
        when (algorithmName) {
            "bonehexact" -> {
                TODO()
                algorithm = TODO()
            }
            "pengbaorange" -> {
                TODO()
                algorithm = TODO()
            }
            "irmaexact" -> {
                TODO()
                algorithm = TODO()
            }
            else -> {
                throw RuntimeException("Attempted to load unknown proof algorithm: ${algorithmName}.")
            }
        }

        this.algorithms[algorithmName] = algorithm
        return algorithm

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

    fun getAlgorithmInstance(schemaName: String): IdentityAlgorithm {
        val schema = this.formats[schemaName]
        val identityAlgorithmClass = this.getAlgorithmClass(schema!!["algorithm"] as String)
        return identityAlgorithmClass.getDeclaredConstructor()
            .newInstance(schemaName, this.formats)
    }

    fun getAlgorithmName(idFormat: String): String {
        return this.formats[idFormat]?.get("algorithm").toString()
    }

    fun deserializePrivate(idFormat: String, privateKey: PrivateKey, attestationBlob: ByteArray): WalletAttestation {
        when (val algorithmName = this.getAlgorithmName(idFormat)) {
            "bonehexact" -> {
                return TODO()
            }
            "pengbaorange" -> {
                return TODO()

            }
            "irmaexact" -> {
                TODO()
                return TODO()
            }
            else -> {
                throw RuntimeException("Attempted to load unknown proof algorithm: ${algorithmName}.")
            }
        }
    }

}
