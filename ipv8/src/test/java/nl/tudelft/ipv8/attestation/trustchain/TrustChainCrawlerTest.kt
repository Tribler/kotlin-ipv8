package nl.tudelft.ipv8.attestation.trustchain

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import io.mockk.coEvery
import io.mockk.spyk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runBlockingTest
import nl.tudelft.ipv8.BaseCommunityTest
import nl.tudelft.ipv8.Peer
import nl.tudelft.ipv8.attestation.trustchain.store.TrustChainSQLiteStore
import nl.tudelft.ipv8.keyvault.JavaCryptoProvider
import nl.tudelft.ipv8.peerdiscovery.Network
import nl.tudelft.ipv8.sqldelight.Database
import org.junit.Assert
import org.junit.Test
import java.util.Date

@OptIn(ExperimentalCoroutinesApi::class)
class TrustChainCrawlerTest : BaseCommunityTest() {
    private fun createTrustChainStore(): TrustChainSQLiteStore {
        val driver: SqlDriver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        Database.Schema.create(driver)
        val database = Database(driver)
        return TrustChainSQLiteStore(database)
    }

    private fun getCommunity(): TrustChainCommunity {
        val settings = TrustChainSettings()
        val store = createTrustChainStore()
        val community = TrustChainCommunity(settings = settings, database = store)
        community.myPeer = getMyPeer()
        community.endpoint = getEndpoint()
        community.network = Network()
        community.maxPeers = 20
        return community
    }

    @Suppress("DEPRECATION") // TODO: rewrite usage of coroutines in testing.
    @Test
    fun crawlChain_noop() =
        runBlockingTest {
            val crawler = TrustChainCrawler()
            val trustChainCommunity = spyk(getCommunity())
            crawler.trustChainCommunity = trustChainCommunity

            val newKey = JavaCryptoProvider.generateKey()

            val block =
                TrustChainBlock(
                    "custom",
                    "hello".toByteArray(Charsets.US_ASCII),
                    newKey.pub().keyToBin(),
                    1u,
                    ANY_COUNTERPARTY_PK,
                    0u,
                    GENESIS_HASH,
                    EMPTY_SIG,
                    Date(),
                )

            trustChainCommunity.database.addBlock(block)

            val peer = Peer(newKey)

            // We have all blocks
            crawler.crawlChain(peer, 1u)
            Assert.assertEquals(1, trustChainCommunity.database.getAllBlocks().size)
        }

    @Suppress("DEPRECATION") // TODO: rewrite usage of coroutines in testing.
    @Test
    fun crawlChain_singleBlock() =
        runBlockingTest {
            val crawler = TrustChainCrawler()
            val trustChainCommunity = spyk(getCommunity())
            crawler.trustChainCommunity = trustChainCommunity

            val newKey = JavaCryptoProvider.generateKey()

            val block1 =
                TrustChainBlock(
                    "custom",
                    "hello".toByteArray(Charsets.US_ASCII),
                    newKey.pub().keyToBin(),
                    1u,
                    ANY_COUNTERPARTY_PK,
                    0u,
                    GENESIS_HASH,
                    EMPTY_SIG,
                    Date(),
                )

            val block2 =
                TrustChainBlock(
                    "custom",
                    "hello".toByteArray(Charsets.US_ASCII),
                    newKey.pub().keyToBin(),
                    2u,
                    ANY_COUNTERPARTY_PK,
                    0u,
                    GENESIS_HASH,
                    EMPTY_SIG,
                    Date(),
                )

            trustChainCommunity.database.addBlock(block1)

            val peer = Peer(newKey)

            coEvery {
                trustChainCommunity.sendCrawlRequest(any(), any(), LongRange(2, 2))
            } answers {
                trustChainCommunity.database.addBlock(block2)
                listOf(block2)
            }

            // Fetch a single block
            crawler.crawlChain(peer, 2u)

            Assert.assertEquals(2, trustChainCommunity.database.getAllBlocks().size)
        }

    @Suppress("DEPRECATION") // TODO: rewrite usage of coroutines in testing.
    @Test
    fun crawlChain_fillGap() =
        runBlockingTest {
            val crawler = TrustChainCrawler()
            val trustChainCommunity = spyk(getCommunity())
            crawler.trustChainCommunity = trustChainCommunity

            val newKey = JavaCryptoProvider.generateKey()

            val block1 =
                TrustChainBlock(
                    "custom",
                    "hello".toByteArray(Charsets.US_ASCII),
                    newKey.pub().keyToBin(),
                    1u,
                    ANY_COUNTERPARTY_PK,
                    0u,
                    GENESIS_HASH,
                    EMPTY_SIG,
                    Date(),
                )

            val block2 =
                TrustChainBlock(
                    "custom",
                    "hello".toByteArray(Charsets.US_ASCII),
                    newKey.pub().keyToBin(),
                    2u,
                    ANY_COUNTERPARTY_PK,
                    0u,
                    GENESIS_HASH,
                    EMPTY_SIG,
                    Date(),
                )

            trustChainCommunity.database.addBlock(block2)

            val peer = Peer(newKey)

            coEvery {
                trustChainCommunity.sendCrawlRequest(any(), any(), LongRange(1, 1))
            } answers {
                trustChainCommunity.database.addBlock(block1)
                listOf(block1)
            }

            // Fetch one block
            crawler.crawlChain(peer, 2u)

            Assert.assertEquals(2, trustChainCommunity.database.getAllBlocks().size)
        }
}
