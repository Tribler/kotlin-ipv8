package nl.tudelft.ipv8.attestation.common

import nl.tudelft.ipv8.attestation.common.consts.AlgorithmNames.BONEH_EXACT
import nl.tudelft.ipv8.attestation.common.consts.AlgorithmNames.IRMA_EXACT
import nl.tudelft.ipv8.attestation.common.consts.AlgorithmNames.PENG_BAO_RANGE
import nl.tudelft.ipv8.attestation.common.consts.SchemaConstants.ID_METADATA
import nl.tudelft.ipv8.attestation.common.consts.SchemaConstants.ID_METADATA_BIG
import nl.tudelft.ipv8.attestation.common.consts.SchemaConstants.ID_METADATA_HUGE
import nl.tudelft.ipv8.attestation.common.consts.SchemaConstants.ID_METADATA_RANGE_18PLUS
import nl.tudelft.ipv8.attestation.common.consts.SchemaConstants.ID_METADATA_RANGE_UNDERAGE
import nl.tudelft.ipv8.attestation.wallet.consts.Cryptography.ALGORITHM
import nl.tudelft.ipv8.attestation.wallet.consts.Cryptography.HASH
import nl.tudelft.ipv8.attestation.wallet.consts.Cryptography.KEY_SIZE
import nl.tudelft.ipv8.attestation.wallet.consts.Cryptography.SHA256
import nl.tudelft.ipv8.attestation.wallet.consts.Cryptography.SHA256_4
import nl.tudelft.ipv8.attestation.wallet.consts.Cryptography.SHA512
import nl.tudelft.ipv8.attestation.wallet.cryptography.IdentityAlgorithm
import nl.tudelft.ipv8.attestation.wallet.cryptography.WalletAttestation
import nl.tudelft.ipv8.attestation.wallet.cryptography.bonehexact.BonehExact
import nl.tudelft.ipv8.attestation.wallet.cryptography.bonehexact.BonehPrivateKey
import nl.tudelft.ipv8.attestation.wallet.cryptography.bonehexact.attestations.BonehAttestation
import nl.tudelft.ipv8.attestation.wallet.cryptography.pengbaorange.PengBaoRange
import nl.tudelft.ipv8.attestation.wallet.cryptography.pengbaorange.attestations.PengBaoAttestation
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
            BONEH_EXACT -> {
                BonehAttestation.deserialize(serialized, idFormat)
            }
            PENG_BAO_RANGE -> {
                PengBaoAttestation.deserialize(serialized, idFormat)
            }
            IRMA_EXACT -> {
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
            BONEH_EXACT -> {
                BonehAttestation.deserializePrivate(privateKey, serialized, idFormat)
            }
            PENG_BAO_RANGE -> {
                PengBaoAttestation.deserializePrivate(privateKey, serialized, idFormat)
            }
            IRMA_EXACT -> {
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
        defaultSchemas.add(AlgorithmScheme(ID_METADATA, BONEH_EXACT, 32, SHA256_4))
        defaultSchemas.add(AlgorithmScheme(ID_METADATA_BIG, BONEH_EXACT, 64, SHA256))
        defaultSchemas.add(AlgorithmScheme(ID_METADATA_HUGE, BONEH_EXACT, 96, SHA512))
        defaultSchemas.add(AlgorithmScheme(ID_METADATA_RANGE_18PLUS, PENG_BAO_RANGE, 32, min = 18, max = 200))
        defaultSchemas.add(AlgorithmScheme(ID_METADATA_RANGE_UNDERAGE, PENG_BAO_RANGE, 32, min = 0, max = 17))

        defaultSchemas.forEach {
            val params = hashMapOf<String, Any>(KEY_SIZE to it.keySize)
            if (it.hashAlgorithm != null) {
                params[HASH] = it.hashAlgorithm
            }
            if (it.min != null && it.max != null) {
                params["min"] = it.min
                params["max"] = it.max
            }

            this.registerSchema(
                it.schemaName,
                it.algorithmName,
                params
            )
        }
    }

    fun getAlgorithmInstance(idFormat: String): IdentityAlgorithm {
        return when (val algorithmName = getAlgorithmName(idFormat)) {
            BONEH_EXACT -> {
                BonehExact(idFormat, this.formats)
            }
            PENG_BAO_RANGE -> {
                PengBaoRange(idFormat, this.formats)
            }
            IRMA_EXACT -> {
                TODO("IRMA is not implemented.")
            }
            else -> {
                throw RuntimeException("Attempted to load unknown proof algorithm: ${algorithmName}.")
            }
        }
    }

    fun getAlgorithmName(idFormat: String): String {
        return this.formats[idFormat]?.get(ALGORITHM).toString()
    }

    fun getSchemaNames(): List<String> {
        return this.formats.keys.toList()
    }
}
