package nl.tudelft.ipv8.messaging.utp

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import mu.KotlinLogging
import net.utp4j.channels.UtpSocketChannel
import net.utp4j.channels.UtpSocketState
import net.utp4j.channels.impl.UtpServerSocketChannelImpl
import net.utp4j.channels.impl.UtpSocketChannelImpl
import net.utp4j.channels.impl.alg.UtpAlgConfiguration
import nl.tudelft.ipv8.IPv4Address
import nl.tudelft.ipv8.Peer
import nl.tudelft.ipv8.messaging.Endpoint
import nl.tudelft.ipv8.messaging.utp.listener.RawResourceListener
import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.nio.ByteBuffer

private val logger = KotlinLogging.logger {}

class UtpEndpoint(
    val port: Int,
    private val ip: InetAddress,
) : Endpoint<Peer>() {

    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.IO + job)
    private var listenerJob: Job = Job()

    var listener = RawResourceListener()

    var lastTime = 0L
    private val sendBuffer = ByteBuffer.allocate(BUFFER_SIZE)
    private val receiveBuffer = ByteBuffer.allocate(BUFFER_SIZE)

    private var serverSocket: CustomUtpServerSocket? = null;
    private var clientSocket: CustomUtpClientSocket? = null;

    init {
        UtpAlgConfiguration.MAX_CWND_INCREASE_PACKETS_PER_RTT = 3000
        logger.error { "THIS IS UTP HERE!!!" }
    }


    override fun isOpen(): Boolean {
        return serverSocket != null && clientSocket != null
    }

    override fun send(peer: Peer, data: ByteArray) {
        send(peer, 13377, data)
    }

    fun send(peer: Peer, port: Int = 13377, data: ByteArray) {
        send(IPv4Address(peer.address.ip, port), data)
    }

    fun send(ipv4Address: IPv4Address, data: ByteArray) {

        // Refresh the buffer
        sendBuffer.clear()
        sendBuffer.put(data)

        scope.launch(Dispatchers.IO) {
            val future = clientSocket?.connect(InetSocketAddress(ipv4Address.ip, ipv4Address.port))
                ?.apply { block() }
            if (future != null) {
                if (future.isSuccessfull) {
                    clientSocket?.write(sendBuffer)?.apply { block() }
                    logger.debug("Sent buffer")
                } else logger.debug("Did not manage to connect to the server!")
            } else {
                logger.debug("Future is null!")
            }
        }
    }

    fun sendRawClientData(packet: DatagramPacket) {
        scope.launch(Dispatchers.IO) {
            clientSocket?.sendRawData(packet)
        }
    }

    fun sendServerData(packet: DatagramPacket) {
        serverSocket?.sendData(packet)
    }

    override fun open() {
        serverSocket = CustomUtpServerSocket()
        serverSocket?.bind(InetSocketAddress(ip, port))

        val c = CustomUtpClientSocket()
        try {
            c.dgSocket = DatagramSocket()
            c.state = UtpSocketState.CLOSED
        } catch (exp: IOException) {
            throw IOException("Could not open UtpSocketChannel: " + exp.message)
        }
        clientSocket = c

        logger.debug("Server started on $ip:$port")
        serverListen()
    }

    override fun close() {
        logger.debug("Stopping the server!")
        listenerJob.cancel()
        serverSocket?.close()
        clientSocket?.close()
    }

    fun getUtpServerSocket(): CustomUtpServerSocket {
        return serverSocket
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

    class CustomUtpServerSocket : UtpServerSocketChannelImpl() {
        var packetListener: (DatagramPacket) -> Unit = {};

        fun sendData(packet: DatagramPacket) {
            socket.send(packet)
        }

        override fun recievePacket(packet: DatagramPacket?) {
            // inform packet listener
            packetListener.invoke(packet!!)

            super.recievePacket(packet)
        }
    }

    class CustomUtpClientSocket : UtpSocketChannelImpl() {

        fun sendRawData(packet: DatagramPacket) {
            dgSocket.send(packet)
        }
    }

}
