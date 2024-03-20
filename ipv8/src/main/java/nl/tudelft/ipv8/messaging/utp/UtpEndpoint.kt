package nl.tudelft.ipv8.messaging.utp

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import mu.KotlinLogging
import net.utp4j.channels.UtpServerSocketChannel
import net.utp4j.channels.UtpSocketChannel
import net.utp4j.channels.futures.UtpReadListener
import net.utp4j.channels.impl.alg.UtpAlgConfiguration
import nl.tudelft.ipv8.IPv4Address
import nl.tudelft.ipv8.Peer
import nl.tudelft.ipv8.messaging.Endpoint
import nl.tudelft.ipv8.messaging.utp.listener.RawResourceListener
import java.net.InetAddress
import java.net.InetSocketAddress
import java.nio.ByteBuffer

private val logger = KotlinLogging.logger {}

class UtpEndpoint(
    private val port: Int,
    private val ip: InetAddress,
) : Endpoint<Peer>() {

    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.IO + job)
    private var listenerJob: Job = Job()

    var listener = RawResourceListener()

    var lastTime = 0L
    private val sendBuffer = ByteBuffer.allocate(BUFFER_SIZE)
    private val receiveBuffer = ByteBuffer.allocate(BUFFER_SIZE)

    private var serverSocket: UtpServerSocketChannel? = null;

    init {
        UtpAlgConfiguration.MAX_CWND_INCREASE_PACKETS_PER_RTT = 3000
    }


    override fun isOpen(): Boolean {
        return serverSocket != null
    }

    override fun send(peer: Peer, data: ByteArray) {
        // Modify the address to use the open UTP port
        // ???

        send(IPv4Address(peer.wanAddress.ip, 13377), data)
    }

    fun send(ipv4Address: IPv4Address, data: ByteArray) {

        // Refresh the buffer
        sendBuffer.clear()
        sendBuffer.put(data)

        scope.launch(Dispatchers.IO) {
            UtpSocketChannel.open().let { channel ->
                val future = channel.connect(InetSocketAddress(ipv4Address.ip, ipv4Address.port))
                    ?.apply { block() }
                if (future != null) {
                    if (future.isSuccessfull) {
                        channel.write(sendBuffer)?.apply { block() }
                        logger.debug("Sent buffer")
                    } else logger.debug("Did not manage to connect to the server!")
                } else {
                    logger.debug("Future is null!")
                }
                channel.close()
            }
        }
    }

    override fun open() {
        serverSocket = UtpServerSocketChannel.open()
        serverSocket?.bind(InetSocketAddress(ip, port))
        logger.debug("Server started on $ip:$port")
        serverListen()
    }

    override fun close() {
        logger.debug("Stopping the server!")
        listenerJob.cancel()
        serverSocket?.close()
    }

    private fun serverListen() {
        var startTime: Long
        var endTime: Long
        listenerJob = scope.launch(Dispatchers.IO) {
            while (isActive) {
                serverSocket?.accept()?.run {
                    block()
                    logger.debug("Receiving new data!")
                    startTime = System.currentTimeMillis()
                    channel.let {
                        it.read(receiveBuffer)?.run {
                            setListener(listener)
                            block()
                        }
                        endTime = System.currentTimeMillis();
                        lastTime = (endTime - startTime)
                        logger.debug("Finished receiving data!")
                    }
                    channel.close()
                }
            }
        }
    }


    companion object {
        const val BUFFER_SIZE = 50_000_000
    }

}
