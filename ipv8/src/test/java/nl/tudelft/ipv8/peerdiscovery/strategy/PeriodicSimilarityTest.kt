package nl.tudelft.ipv8.peerdiscovery.strategy

import io.mockk.*
import nl.tudelft.ipv8.IPv4Address
import nl.tudelft.ipv8.Peer
import nl.tudelft.ipv8.peerdiscovery.DiscoveryCommunity
import nl.tudelft.ipv8.peerdiscovery.Network
import org.junit.Test

class PeriodicSimilarityTest {
    @Test
    fun takeStep() {
        val overlay = mockk<DiscoveryCommunity>(relaxed = true)
        val network = mockk<Network>()
        val peer = mockk<Peer>()
        val mockAddress = IPv4Address("1.2.3.4", 1234)
        every { overlay.network } returns network
        every { network.getRandomPeer() } returns peer
        every { peer.address } returns mockAddress

        val periodicSimilarity = PeriodicSimilarity(overlay)
        periodicSimilarity.takeStep()

        verify { network.getRandomPeer() }
        verify { overlay.sendSimilarityRequest(any()) }
    }
}
