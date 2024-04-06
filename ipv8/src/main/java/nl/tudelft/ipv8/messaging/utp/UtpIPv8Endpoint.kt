package nl.tudelft.ipv8.messaging.utp

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import net.utp4j.channels.UtpServerSocketChannel
import net.utp4j.channels.UtpSocketChannel
import net.utp4j.channels.UtpSocketState
import net.utp4j.channels.impl.UtpServerSocketChannelImpl
import net.utp4j.channels.impl.UtpSocketChannelImpl
import net.utp4j.channels.impl.alg.UtpAlgConfiguration
import net.utp4j.channels.impl.recieve.UtpRecieveRunnable
import nl.tudelft.ipv8.IPv4Address
import nl.tudelft.ipv8.messaging.Endpoint
import nl.tudelft.ipv8.messaging.utp.listener.RawResourceListener
import java.io.IOException
import java.net.BindException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetSocketAddress
import java.nio.ByteBuffer

class UtpIPv8Endpoint : Endpoint<IPv4Address>() {

    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.IO + job)

    private var listenerJob: Job = Job()

    var listener = RawResourceListener()

    private val sendBuffer = ByteBuffer.allocate(BUFFER_SIZE)
    private val receiveBuffer = ByteBuffer.allocate(BUFFER_SIZE)

    private var serverSocket: CustomUtpServerSocket? = null;
    private var clientSocket: UtpSocketChannel? = null;

    var udpSocket: DatagramSocket? = null
    private var clientUtpSocket: UtpSocket? = null
    private var serverUtpSocket: UtpSocket? = null

    init {
        UtpAlgConfiguration.MAX_CWND_INCREASE_PACKETS_PER_RTT = 3000
        UtpAlgConfiguration.MAX_PACKET_SIZE = MAX_UTP_PACKET_SIZE
        println("Utp IPv8 endpoint initialized!")
    }

    override fun isOpen(): Boolean = serverSocket != null && clientSocket != null

    override fun open() {
        clientUtpSocket = UtpSocket(udpSocket)
        serverUtpSocket = UtpSocket(udpSocket)

        serverSocket = CustomUtpServerSocket()
        serverSocket?.bind(serverUtpSocket!!)

        val c = UtpSocketChannelImpl()
        try {
            c.dgSocket = clientUtpSocket
            c.state = UtpSocketState.CLOSED
        } catch (exp: IOException) {
            throw IOException("Could not open UtpSocketChannel: " + exp.message)
        }
        clientSocket = c

        println("UTP server started listening!")
        serverListen()
    }

    override fun close() {
        println("Stopping the server!")
        listenerJob.cancel()
        serverSocket?.close()
        clientSocket?.close()
    }

    override fun send(peer: IPv4Address, data: ByteArray) {
        // Refresh the buffer
        sendBuffer.clear()
        sendBuffer.put(data)

        scope.launch(Dispatchers.IO) {
            val future = clientSocket?.connect(InetSocketAddress(peer.ip, peer.port))
                ?.apply { block() }
            if (future != null) {
                if (future.isSuccessfull) {
                    clientSocket?.write(sendBuffer)?.apply { block() }
                    println("Sent buffer")
                } else println("Did not manage to connect to the server!")
            } else {
                println("Future is null!")
            }
        }
    }

    private fun serverListen() {
        listenerJob = scope.launch(Dispatchers.IO) {
            while (isActive) {
                serverSocket?.accept()?.run {
                    block()
                    println("Receiving new data!")
                    channel.let {
                        it.read(receiveBuffer)?.run {
                            setListener(listener)
                            block()
                        }
                        println("Finished receiving data!")
                    }
                    channel.close()
                }
            }
        }
    }

    fun onPacket(receivePacket: DatagramPacket) {
        val packet = DatagramPacket(ByteArray(receivePacket.length - 1), receivePacket.length - 1)
        val data = receivePacket.data.copyOfRange(1, receivePacket.length)
        packet.setData(data, 0, data.size)

        // Send the packet to the UTP socket on both (???) sides
        // TODO: Should probably distinguish between client and server connection
        clientUtpSocket?.buffer?.trySend(packet)?.isSuccess
        serverUtpSocket?.buffer?.trySend(packet)?.isSuccess
    }

    companion object {
        const val PREFIX_UTP: Byte = 0x42;
        // 1500 - 20 (IPv4 header) - 8 (UDP header) - 1 (UTP prefix)
        const val MAX_UTP_PACKET_SIZE = 1471;
        const val BUFFER_SIZE = 50_000_000
    }

    class CustomUtpServerSocket : UtpServerSocketChannelImpl() {

        override fun bind(addr: InetSocketAddress?) {
            throw BindException("Cannot bind new socket, use existing one!")
        }

        fun bind(utpSocket: DatagramSocket) {
            this.socket = utpSocket
            listenRunnable = UtpRecieveRunnable(utpSocket, this)
        }

    }
}
