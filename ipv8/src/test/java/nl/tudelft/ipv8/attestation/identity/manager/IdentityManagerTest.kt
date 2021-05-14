package nl.tudelft.ipv8.attestation.identity.manager

import com.squareup.sqldelight.db.SqlDriver
import com.squareup.sqldelight.sqlite.driver.JdbcSqliteDriver
import nl.tudelft.ipv8.attestation.identity.store.Credential
import nl.tudelft.ipv8.attestation.identity.store.IdentitySQLiteStore
import nl.tudelft.ipv8.keyvault.defaultCryptoProvider
import nl.tudelft.ipv8.sqldelight.Database
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IdentityManagerTest {

    private val privateKey = defaultCryptoProvider.generateKey()
    private val publicKey = privateKey.pub()
    private val authorityPrivateKey = defaultCryptoProvider.generateKey()
    private val authorityPublicKey = authorityPrivateKey.pub()
    private val manager: IdentityManager

    private var driver: SqlDriver

    init {
        driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        Database.Schema.create(driver)
        val database = Database(driver)
        val store = IdentitySQLiteStore(database)
        this.manager = IdentityManager(store)
    }

    private fun forgetIdentities() {
        this.manager.pseudonyms.clear()
        driver.close()
        driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        Database.Schema.create(driver)
        val database = Database(driver)
        this.manager.database = IdentitySQLiteStore(database)
    }

    @Test
    fun testCreateIdentity() {
        val pseudonym = this.manager.getPseudonym(this.privateKey)
        assertEquals(listOf<Credential>(), pseudonym.getCredentials())
    }

    @Test
    fun testSubstantiateEmpty() {
        val (correct, pseudonym) = this.manager.substantiate(
            this.publicKey,
            byteArrayOf(),
            byteArrayOf(),
            byteArrayOf(),
            byteArrayOf()
        )

        assertTrue(correct)
        assertEquals(listOf<Credential>(), pseudonym.getCredentials())
    }

    @Test
    fun testCreateCredential() {
        val pseudonym = this.manager.getPseudonym(this.privateKey)
        pseudonym.createCredential("ab".repeat(16).toByteArray(), hashMapOf("some_key" to "some_value"))
        assertEquals(1, pseudonym.getCredentials().size)
        assertEquals(0, pseudonym.getCredentials()[0].attestations.size)
        val expectedMD = JSONObject()
        expectedMD.put("some_key", "some_value")
        assertEquals(expectedMD.toString(), String(pseudonym.getCredentials()[0].metadata.serializedMetadata))
    }

    @Test
    fun testSubstantiateCredentialUpdate() {
        val pseudonym = this.manager.getPseudonym(this.privateKey)
        pseudonym.createCredential("ab".repeat(16).toByteArray(), hashMapOf("some_key" to "some_value"))
        val (metadata, tokens, attestations, authorities) = pseudonym.discloseCredentials(
            pseudonym.getCredentials(),
            setOf()
        )
        this.manager.pseudonyms.clear()
        val (correct, publicPseudonym) = this.manager.substantiate(
            pseudonym.publicKey,
            metadata,
            tokens,
            attestations,
            authorities
        )

        assertTrue(correct)
        assertEquals(1, publicPseudonym.getCredentials().size)
        assertEquals(0, publicPseudonym.getCredentials()[0].attestations.size)
        val expectedMD = JSONObject()
        expectedMD.put("some_key", "some_value")
        assertEquals(expectedMD.toString(), String(publicPseudonym.getCredentials()[0].metadata.serializedMetadata))
    }

    @Test
    fun testSubstantiateCredentialWithoutMetadata() {
        val pseudonym = this.manager.getPseudonym(this.privateKey)
        pseudonym.createCredential("ab".repeat(16).toByteArray(), hashMapOf("some_key" to "some_value"))
        val (_, tokens, attestations, authorities) = pseudonym.discloseCredentials(
            pseudonym.getCredentials(),
            setOf()
        )
        this.forgetIdentities()
        val (correct, publicPseudonym) = this.manager.substantiate(
            pseudonym.publicKey,
            byteArrayOf(),
            tokens,
            attestations,
            authorities
        )

        assertTrue(correct)
        assertEquals(0, publicPseudonym.getCredentials().size)
        assertEquals(1, publicPseudonym.tree.elements.size)
    }

    @Test
    fun testSubstantiateCredentialWithMetadata() {
        val pseudonym = this.manager.getPseudonym(this.privateKey)
        pseudonym.createCredential("ab".repeat(16).toByteArray(), hashMapOf("some_key" to "some_value"))
        val (metadata, tokens, attestations, authorities) = pseudonym.discloseCredentials(
            pseudonym.getCredentials(),
            setOf()
        )
        this.forgetIdentities()
        val (correct, publicPseudonym) = this.manager.substantiate(
            pseudonym.publicKey,
            metadata,
            tokens,
            attestations,
            authorities
        )

        assertTrue(correct)
        assertEquals(1, publicPseudonym.getCredentials().size)
        assertEquals(0, publicPseudonym.getCredentials()[0].attestations.size)
        val expectedMD = JSONObject()
        expectedMD.put("some_key", "some_value")
        assertEquals(expectedMD.toString(), String(publicPseudonym.getCredentials()[0].metadata.serializedMetadata))
    }

    @Test
    fun testSubstantiateCredentialFull() {
        val pseudonym = this.manager.getPseudonym(this.privateKey)
        pseudonym.createCredential("ab".repeat(16).toByteArray(), hashMapOf("some_key" to "some_value"))

        val attestation = pseudonym.createAttestation(pseudonym.getCredentials()[0].metadata, this.authorityPrivateKey)
        pseudonym.addAttestation(this.authorityPublicKey, attestation)

        val (metadata, tokens, attestations, authorities) = pseudonym.discloseCredentials(
            pseudonym.getCredentials(),
            setOf(attestation.hash)
        )
        this.forgetIdentities()

        val (correct, publicPseudonym) = this.manager.substantiate(
            pseudonym.publicKey,
            metadata,
            tokens,
            attestations,
            authorities
        )

        assertTrue(correct)
        assertEquals(1, publicPseudonym.getCredentials().size)
        assertEquals(1, publicPseudonym.getCredentials()[0].attestations.size)
        val expectedMD = JSONObject()
        expectedMD.put("some_key", "some_value")
        assertEquals(expectedMD.toString(), String(publicPseudonym.getCredentials()[0].metadata.serializedMetadata))
    }

    @Test
    fun testSubstantiateCredentialPartial() {
        val pseudonym = this.manager.getPseudonym(this.privateKey)
        pseudonym.createCredential("ab".repeat(16).toByteArray(), hashMapOf("some_key" to "some_value"))

        val attestation = pseudonym.createAttestation(pseudonym.getCredentials()[0].metadata, this.authorityPrivateKey)
        pseudonym.addAttestation(this.authorityPublicKey, attestation)

        val attestation2 = pseudonym.createAttestation(pseudonym.getCredentials()[0].metadata, this.privateKey)
        pseudonym.addAttestation(this.publicKey, attestation2)

        val (metadata, tokens, attestations, authorities) = pseudonym.discloseCredentials(
            pseudonym.getCredentials(),
            setOf(attestation.hash)
        )
        this.forgetIdentities()

        val (correct, publicPseudonym) = this.manager.substantiate(
            pseudonym.publicKey,
            metadata,
            tokens,
            attestations,
            authorities
        )

        assertTrue(correct)
        assertEquals(1, publicPseudonym.getCredentials().size)
        assertEquals(1, publicPseudonym.getCredentials()[0].attestations.size)
        val expectedMD = JSONObject()
        expectedMD.put("some_key", "some_value")
        assertEquals(expectedMD.toString(), String(publicPseudonym.getCredentials()[0].metadata.serializedMetadata))
    }
}


