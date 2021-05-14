package nl.tudelft.ipv8.attestation.identity.datastructures.tokenTree

import nl.tudelft.ipv8.keyvault.PrivateKey
import nl.tudelft.ipv8.keyvault.PublicKey
import nl.tudelft.ipv8.keyvault.defaultCryptoProvider
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class TokenTest {

    private lateinit var privateKey: PrivateKey
    private lateinit var testPublicKey: PublicKey
    private lateinit var testData: ByteArray
    private lateinit var testDataHash: ByteArray
    private lateinit var testSignature: ByteArray

    @Before
    fun init() {
        privateKey = defaultCryptoProvider.generateKey()
        testData = "1234567890abcdefghijklmnopqrstuvwxyz".repeat(69).toByteArray()

        val sk = defaultCryptoProvider.generateKey()
        testPublicKey = sk.pub()

        val token = Token("test_previous_token_hash".toByteArray(), content = testData, privateKey = sk)
        testDataHash = token.contentHash
        testSignature = token.signature
    }

    @Test
    fun testCreatePrivateToken() {
        val token =
            Token("test_previous_token_hash".toByteArray(), content = this.testData, privateKey = this.privateKey)
        assertArrayEquals("test_previous_token_hash".toByteArray(), token.previousTokenHash)
        assertArrayEquals(this.testData, token.content)
        assertArrayEquals(this.testDataHash, token.contentHash)
        assertTrue(token.verify(this.privateKey.pub()))
    }

    @Test
    fun testVerifyTokenIllegal() {
        val token =
            Token("test_previous_token_hash".toByteArray(), contentHash = this.testData, signature = this.testSignature)
        val otherKey = defaultCryptoProvider.generateKey().pub()
        assertFalse(token.verify(otherKey))
    }

    @Test
    fun testUpdatePublicToken() {
        val token = Token(
            "test_previous_token_hash".toByteArray(),
            contentHash = this.testDataHash,
            signature = this.testSignature
        )
        val returnValue = token.receiveContent(this.testData)

        assertTrue(returnValue)
        assertArrayEquals("test_previous_token_hash".toByteArray(), token.previousTokenHash)
        assertArrayEquals(this.testData, token.content)
        assertArrayEquals(this.testDataHash, token.contentHash)
        assertTrue(token.verify(this.testPublicKey))
    }

    @Test
    fun testUpdatePublicTokenIllegal() {
        val token = Token(
            "test_previous_token_hash".toByteArray(),
            contentHash = this.testDataHash,
            signature = this.testSignature
        )
        val returnValue = token.receiveContent("some other data".toByteArray())

        assertFalse(returnValue)
        assertArrayEquals("test_previous_token_hash".toByteArray(), token.previousTokenHash)
        assertNull(token.content)
        assertArrayEquals(this.testDataHash, token.contentHash)
        assertTrue(token.verify(this.testPublicKey))
    }
}
