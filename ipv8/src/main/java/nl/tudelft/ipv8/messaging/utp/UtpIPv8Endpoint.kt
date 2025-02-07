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
import nl.tudelft.ipv8.Peer
import nl.tudelft.ipv8.messaging.Endpoint
import nl.tudelft.ipv8.messaging.EndpointListener
import nl.tudelft.ipv8.messaging.Packet
import nl.tudelft.ipv8.messaging.payload.TransferRequestPayload
import nl.tudelft.ipv8.messaging.utp.listener.BaseDataListener
import nl.tudelft.ipv8.messaging.utp.listener.RawResourceListener
import nl.tudelft.ipv8.messaging.utp.listener.TransferListener
import java.io.IOException
import java.net.BindException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetSocketAddress
import java.nio.ByteBuffer

class UtpIPv8Endpoint : Endpoint<IPv4Address>(), EndpointListener {

    /**
     * Scope used for network operations
     */
    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.IO + job)

    /**
     * Job for the UTP server listener
     */
    private var listenerJob: Job = Job()

    /**
     * Listener for raw resources used by the UTP server (receiver)
     */
    var listener: TransferListener = RawResourceListener()

    private val sendBuffer = ByteBuffer.allocate(getBufferSize())
    private val receiveBuffer = ByteBuffer.allocate(getBufferSize())

    private var serverSocket: CustomUtpServerSocket? = null;
    var clientSocket: CustomUtpSocket? = null;

    /**
     * The underlying UDP socket used by IPv8
     */
    var udpSocket: DatagramSocket? = null
    private var clientUtpSocket: UtpSocket? = null
    private var serverUtpSocket: UtpSocket? = null

    var rawPacketListeners: MutableList<(DatagramPacket, Boolean) -> Unit> = ArrayList()
    val permittedTransfers = mutableMapOf<IPv4Address, TransferRequestPayload?>()

    private var currentLan: IPv4Address? = null

    /**
     * Initializes the UTP IPv8 endpoint and the UTP configuration in the library
     */
    init {
        UtpAlgConfiguration.MAX_CWND_INCREASE_PACKETS_PER_RTT = 30000
        UtpAlgConfiguration.MAX_PACKET_SIZE = MAX_UTP_PACKET_SIZE
        println("Utp IPv8 endpoint initialized!")
    }

    override fun isOpen(): Boolean = serverSocket != null && clientSocket != null

    override fun open() {
        clientUtpSocket = UtpSocket(udpSocket)
        serverUtpSocket = UtpSocket(udpSocket)

        serverSocket = CustomUtpServerSocket()
        serverSocket?.bind(serverUtpSocket!!)

        val c = CustomUtpSocket()
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
            clientSocket?.close()
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
                    permittedTransfers.clear()
                    channel.close()
                }
            }
        }
    }

    /**
     * This method is called when a packet is received by the IPv8 UDP socket.
     * It strips the UTP prefix and sends the packet to the UTP socket.
     */
    fun onPacket(receivePacket: DatagramPacket) {
        val packet = DatagramPacket(ByteArray(receivePacket.length - 1), receivePacket.length - 1)
        val data = receivePacket.data.copyOfRange(1, receivePacket.length)
        packet.setData(data, 0, data.size)
        packet.address = receivePacket.address
        packet.port = receivePacket.port

        val receiverIp = IPv4Address(receivePacket.address.hostAddress, receivePacket.port)

        // Send the packet to the UTP socket on both sides
        // TODO: Should probably distinguish between client and server connection
        clientUtpSocket?.buffer?.trySend(packet)?.isSuccess

        // Only allow transfers for accepted files
        if (permittedTransfers.containsKey(receiverIp) || receiverIp == currentLan) {
            val payload = permittedTransfers[receiverIp]
            if (payload != null) {
                // Ensure if the transfer is accepted and the data size is within the buffer size
                if (payload.dataSize > getBufferSize() || payload.status != TransferRequestPayload.TransferStatus.ACCEPT)
                    return
                receiveBuffer.limit(payload.dataSize)
                listener = when (payload.type) {
                    TransferRequestPayload.TransferType.FILE -> {
                        RawResourceListener()
                    }
                    TransferRequestPayload.TransferType.RANDOM_DATA -> {
                        BaseDataListener()
                    }
                }
                permittedTransfers[receiverIp] = null
            }
            // TODO: In some cases this check is printed, but the buffer should have space (needs more investigation)
            if (receiveBuffer.remaining() < packet.length) {
                println("Buffer overflow!")
                return
            }
            serverUtpSocket?.buffer?.trySend(packet)?.isSuccess
        }

        // send packet to rawPacketListeners
        rawPacketListeners.forEach { listener -> listener.invoke(packet, true)}
    }

    companion object {
        const val PREFIX_UTP: Byte = 0x42
        // 1500 - 20 (IPv4 header) - 8 (UDP header) - 1 (UTP prefix)
        const val MAX_UTP_PACKET_SIZE = 1471
        // Hardcoded maximum buffer size of 50 MB + UTP packet size (for processing)
        private const val BUFFER_SIZE = 50_000_000 + MAX_UTP_PACKET_SIZE

        fun getBufferSize(): Int {
            return BUFFER_SIZE
        }
    }



    /**
     * A custom UTP server socket implementation to change the bind method to use an existing socket.
     * The original method throws an exception as we don't want to bind a new socket.
     */
    class CustomUtpServerSocket : UtpServerSocketChannelImpl() {
         override fun bind(addr: InetSocketAddress?) {
            throw BindException("Cannot bind new socket, use existing one!")
        }

        fun bind(utpSocket: DatagramSocket) {
            this.socket = utpSocket
            listenRunnable = UtpRecieveRunnable(utpSocket, this)
        }
    }

    /**
     * A custom UTP socket implementation to allow for logging of raw packets.
     */
    class CustomUtpSocket: UtpSocketChannelImpl() {
        var rawPacketListeners: MutableList<(DatagramPacket, Boolean) -> Unit> = ArrayList()

        override fun sendPacket(pkt: DatagramPacket?) {
            rawPacketListeners.forEach { listener -> listener.invoke(pkt!!, false) }
            super.sendPacket(pkt)
        }
    }

    override fun onPacket(packet: Packet) {}

    override fun onEstimatedLanChanged(address: IPv4Address) {
        currentLan = address
    }
}
