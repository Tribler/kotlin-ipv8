package nl.tudelft.ipv8.attestation.identity.datastructures.tokenTree

import nl.tudelft.ipv8.keyvault.PrivateKey
import nl.tudelft.ipv8.keyvault.PublicKey
import nl.tudelft.ipv8.keyvault.defaultCryptoProvider
import nl.tudelft.ipv8.util.hexToBytes
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class TokenTreeTest {

    private lateinit var privateKey: PrivateKey
    private lateinit var publicKey: PublicKey

    @Before
    fun init() {
        privateKey = defaultCryptoProvider.generateKey()
        publicKey = privateKey.pub()
    }

    @Test
    fun testCreateOwnEmpty() {
        val tree = TokenTree(privateKey = privateKey)
        assertEquals(0, tree.elements.size)
        assertEquals(0, tree.unchained.size)
        assertEquals(0, tree.getMissing().size)
    }

    @Test
    fun testCreateOtherEmpty() {
        val tree = TokenTree(publicKey = publicKey)
        assertEquals(0, tree.elements.size)
        assertEquals(0, tree.unchained.size)
        assertEquals(0, tree.getMissing().size)
    }

    @Test
    fun testOwnAdd() {
        val tree = TokenTree(privateKey = privateKey)
        val content = "test content".toByteArray()
        val token = tree.add(content)

        assertEquals(1, tree.elements.size)
        assertEquals(0, tree.unchained.size)
        assertEquals(0, tree.getMissing().size)
        assertTrue(tree.verify(token))
        assertArrayEquals(content, token.content)
    }

    @Test
    fun testOtherAddInSequence() {
        val tree = TokenTree(privateKey = privateKey)
        val actualToken = Token(tree.genesisHash, content = "some data".toByteArray(), privateKey = privateKey)
        val publicToken = Token.deserialize(actualToken.getPlaintextSigned(), this.publicKey)
        val token = tree.gatherToken(publicToken)!!

        assertEquals(1, tree.elements.size)
        assertEquals(0, tree.unchained.size)
        assertEquals(0, tree.getMissing().size)
        assertTrue(tree.verify(publicToken))
        assertNull(token.content)
    }

    @Test
    fun testOtherAddOutSequence() {
        val tree = TokenTree(publicKey = publicKey)
        val actualToken =
            Token("ab".repeat(16).toByteArray(), content = "some data".toByteArray(), privateKey = privateKey)
        val publicToken = Token.deserialize(actualToken.getPlaintextSigned(), this.publicKey)
        val token = tree.gatherToken(publicToken)

        assertEquals(0, tree.elements.size)
        assertEquals(1, tree.unchained.size)
        assertArrayEquals(arrayOf("ab".repeat(16).toByteArray()), tree.getMissing().toTypedArray())
        assertFalse(tree.verify(publicToken))
        assertNull(token)
    }

    @Test
    fun testOtherAddOutSequenceOverflow() {
        val tree = TokenTree(publicKey = publicKey)
        tree.unchainedLimit = 1

        val realToken1 =
            Token("ab".repeat(16).toByteArray(), content = "some data".toByteArray(), privateKey = privateKey)
        val realToken2 =
            Token("cd".repeat(16).toByteArray(), content = "some other data".toByteArray(), privateKey = privateKey)
        val publicToken1 = Token.deserialize(realToken1.getPlaintextSigned(), this.publicKey)
        val publicToken2 = Token.deserialize(realToken2.getPlaintextSigned(), this.publicKey)
        tree.gatherToken(publicToken1)
        tree.gatherToken(publicToken2)

        assertEquals(0, tree.elements.size)
        assertEquals(1, tree.unchained.size)
        assertArrayEquals(arrayOf("cd".repeat(16).toByteArray()), tree.getMissing().toTypedArray())
        assertFalse(tree.verify(publicToken1))
        assertFalse(tree.verify(publicToken2))
    }

    @Test
    fun testOtherAddDuplicate() {
        val tree = TokenTree(publicKey = publicKey)
        val actualToken = Token(tree.genesisHash, content = "some data".toByteArray(), privateKey = privateKey)
        val publicToken = Token.deserialize(actualToken.getPlaintextSigned(), this.publicKey)
        val token = tree.gatherToken(publicToken)!!

        assertEquals(1, tree.elements.size)
        assertEquals(0, tree.unchained.size)
        assertEquals(0, tree.getMissing().size)
        assertTrue(tree.verify(publicToken))
        assertNull(token.content)
    }

    @Test
    fun testOtherAddDuplicateContent() {
        val tree = TokenTree(publicKey = publicKey)
        val actualToken = Token(tree.genesisHash, content = "some data".toByteArray(), privateKey = privateKey)
        val publicToken = Token.deserialize(actualToken.getPlaintextSigned(), this.publicKey)
        val token = tree.gatherToken(publicToken)!!
        tree.gatherToken(actualToken)

        assertEquals(1, tree.elements.size)
        assertEquals(0, tree.unchained.size)
        assertEquals(0, tree.getMissing().size)
        assertTrue(tree.verify(publicToken))
        assertTrue(tree.verify(token))
        assertArrayEquals("some data".toByteArray(), token.content)
    }

    @Test
    fun testSerializePublic() {
        val tree = TokenTree(privateKey = privateKey)
        tree.add("data1".toByteArray())
        tree.add("data2".toByteArray())
        tree.add("data3".toByteArray())

        assertEquals(128 * 3, tree.serializePublic().size)
    }

    @Test
    fun testSerializePublicPartial() {
        val tree = TokenTree(privateKey = privateKey)
        val token1 = tree.add("data1".toByteArray())
        val token2 = tree.add("data2".toByteArray(), token1)
        tree.add("data3".toByteArray(), token2)

        assertEquals(128 * 2, tree.serializePublic(token2).size)
    }

    @Test
    fun testDeserializePublic() {
        val publicKey =
            defaultCryptoProvider.keyFromPublicBin("4c69624e61434c504b3ae2db18b81ae520ca5819d5a65c45edb3536ef7cbd86254145a5e96b6ceecd0779d6c7ab811bdb49e1d161b5c49fc29063b4684a0e2d987cd1c91a3fbdedf6005".hexToBytes())
        val serializedTree =
            "c0332fa9b25cf0ae4f2456e9d1c38d41b990faae81e9a6a3610032e96a86ce01f3ab3ec93cbecf2ae8280f0d782111ab3202dfa6eef6797203b886e215fc7b6e3ce8676f8b395bd1e729fe2b2be9865c9d6b9dd7350989d85bf91e1522bc75b21574790b662b5cdcceb5168622707a3ba87fb60db8f89df5e089e7499cf89204c0332fa9b25cf0ae4f2456e9d1c38d41b990faae81e9a6a3610032e96a86ce011feff33a31fe7bd710f738ec96e95a8a8551a2047abd46309b7b2594e606359529e3b4d6f18432c4ac3a73c845a7c33a7d17f476b059689be65fe38bd59c48f4f108e3e4c32ab26e0deadd4fee83f2ff6ca1251fd6b2e0bbdef6fb4f8253260bc0332fa9b25cf0ae4f2456e9d1c38d41b990faae81e9a6a3610032e96a86ce014c75b310b769b0a0c8521a40968af43daf62cec5ab1a65dc658093192020897c82a3baf38bece1d113783a4aefc04e4baf40c8af53d23451823706857830b177db6980c9251072916445bab2b8430d917287d5f5f61496f30bd27dc6ce79d309".hexToBytes()

        val tree = TokenTree(publicKey = publicKey)
        tree.deserializePublic(serializedTree)

        assertEquals(3, tree.elements.size)

        for (token in tree.elements.values) {
            assertTrue(tree.verify(token))
        }

        for (content in listOf("data1".toByteArray(), "data2".toByteArray(), "data3".toByteArray())) {
            assertTrue(tree.elements.values.any { it.receiveContent(content) })
        }
    }
}
