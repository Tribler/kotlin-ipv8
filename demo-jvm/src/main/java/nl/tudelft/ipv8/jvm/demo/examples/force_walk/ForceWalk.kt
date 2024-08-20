package nl.tudelft.ipv8.jvm.demo.examples.force_walk

import kotlinx.coroutines.*
import mu.KotlinLogging
import nl.tudelft.ipv8.*
import nl.tudelft.ipv8.keyvault.JavaCryptoProvider
import nl.tudelft.ipv8.messaging.EndpointAggregator
import nl.tudelft.ipv8.messaging.udp.UdpEndpoint
import java.net.InetAddress
import java.util.*
import kotlin.math.roundToInt

class Application {

    private val scope = CoroutineScope(Dispatchers.Default)
    private val logger = KotlinLogging.logger {}
    lateinit var ipv8: IPv8

    fun run() {
        startIpv8()
    }

    private fun createConnectionCommunity(): OverlayConfiguration<ConnectionCommunity> {
        // Add no walkers on purpose.
        return OverlayConfiguration(
            Overlay.Factory(ConnectionCommunity::class.java), listOf()
        )
    }

    private fun startIpv8() {
        val myKey = JavaCryptoProvider.generateKey()
        val myPeer = Peer(myKey)
        val udpEndpoint = UdpEndpoint(8090, InetAddress.getByName("0.0.0.0"))
        val endpoint = EndpointAggregator(udpEndpoint, null)

        val config = IPv8Configuration(
            overlays = listOf(
                createConnectionCommunity()
            ), walkerInterval = 1.0
        )

        this.ipv8 = IPv8(endpoint, config, myPeer)
        this.ipv8.start()

        scope.launch {
            while (true) {
                for ((_, overlay) in ipv8.overlays) {
                    printPeersInfo(overlay)
                }
                logger.info("===")
                delay(5000)
            }
        }

        while (ipv8.isStarted()) {
            Thread.sleep(1000)
        }
    }

    private fun printPeersInfo(overlay: Overlay) {
        val peers = overlay.getPeers()
        logger.info(overlay::class.simpleName + ": ${peers.size} peers")
        for (peer in peers) {
            val avgPing = peer.getAveragePing()
            val lastRequest = peer.lastRequest
            val lastResponse = peer.lastResponse

            val lastRequestStr =
                if (lastRequest != null) "" + ((Date().time - lastRequest.time) / 1000.0).roundToInt() + " s" else "?"

            val lastResponseStr =
                if (lastResponse != null) "" + ((Date().time - lastResponse.time) / 1000.0).roundToInt() + " s" else "?"

            val avgPingStr = if (!avgPing.isNaN()) "" + (avgPing * 1000).roundToInt() + " ms" else "? ms"
            logger.info("${peer.mid} (S: ${lastRequestStr}, R: ${lastResponseStr}, ${avgPingStr})")
        }
    }
}

fun main(): Unit = runBlocking {
    // Start two peers
    val peer0 = Application()
    val peer1 = Application()

    val scope = CoroutineScope(Dispatchers.Default)

    scope.launch { peer0.run() }
    scope.launch { peer1.run() }

    // Wait for the peers to start
    delay(1000)

    // Walk to each other
    val peer0Address = peer0.ipv8.getOverlay<ConnectionCommunity>()!!.myEstimatedLan
    val peer1Address = peer1.ipv8.getOverlay<ConnectionCommunity>()!!.myEstimatedLan

    peer0.ipv8.getOverlay<ConnectionCommunity>()!!.walkTo(peer1Address)
    peer1.ipv8.getOverlay<ConnectionCommunity>()!!.walkTo(peer0Address)

    // Wait forever
    delay(Long.MAX_VALUE)
}
