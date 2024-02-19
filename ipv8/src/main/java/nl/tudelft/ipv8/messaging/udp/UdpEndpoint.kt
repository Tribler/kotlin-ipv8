package nl.tudelft.ipv8.messaging.udp

import kotlinx.coroutines.*
import mu.KotlinLogging
import nl.tudelft.ipv8.Community
import nl.tudelft.ipv8.IPv4Address
import nl.tudelft.ipv8.Peer
import nl.tudelft.ipv8.messaging.Endpoint
import nl.tudelft.ipv8.messaging.EndpointListener
import nl.tudelft.ipv8.messaging.Packet
import nl.tudelft.ipv8.messaging.tftp.TFTPEndpoint
import java.io.IOException
import java.net.*

private val logger = KotlinLogging.logger {}

open class UdpEndpoint(
    private val port: Int,
    private val ip: InetAddress,
    private val tftpEndpoint: TFTPEndpoint = TFTPEndpoint(),
) : Endpoint<Peer>() {
    private var socket: DatagramSocket? = null

    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.IO + job)

    private var bindJob: Job? = null
    private var lanEstimationJob: Job? = null

    init {
        tftpEndpoint.addListener(
            object : EndpointListener {
                override fun onPacket(packet: Packet) {
                    logger.debug(
                        "Received TFTP packet (${packet.data.size} B) from ${packet.source}",
                    )
                    notifyListeners(packet)
                }

                override fun onEstimatedLanChanged(address: IPv4Address) {
                }
            },
        )
    }

    override fun isOpen(): Boolean {
        return socket?.isBound == true
    }

    override fun send(
        peer: Peer,
        data: ByteArray,
    ) {
        if (!isOpen()) throw IllegalStateException("UDP socket is closed")

        val address = peer.address

        scope.launch {
            logger.debug("Send packet (${data.size} B) to $address ($peer)")
            try {
                if (data.size > UDP_PAYLOAD_LIMIT) {
                    if (peer.supportsTFTP) {
                        tftpEndpoint.send(address, data)
                    } else {
                        logger.warn {
                            "The packet is larger then UDP_PAYLOAD_LIMIT and the peer " +
                                "does not support TFTP"
                        }
                    }
                } else {
                    send(address, data)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun send(
        address: IPv4Address,
        data: ByteArray,
    ) = scope.launch(Dispatchers.IO) {
        try {
            val datagramPacket = DatagramPacket(data, data.size, address.toSocketAddress())
            socket?.send(datagramPacket)
        } catch (e: Exception) {
            logger.error("Sending DatagramPacket failed", e)
        }
    }

    override fun open() {
        val socket = getDatagramSocket()
        this.socket = socket

        tftpEndpoint.socket = socket
        tftpEndpoint.open()

        logger.info { "Opened UDP socket on port ${socket.localPort}" }

        startLanEstimation()

        bindJob = bindSocket(socket)
    }

    /**
     * Finds the nearest unused socket.
     */
    private fun getDatagramSocket(): DatagramSocket {
        for (i in 0 until 100) {
            try {
                return DatagramSocket(port + i, ip)
            } catch (e: Exception) {
                // Try another port
            }
        }
        // Use any available port
        return DatagramSocket()
    }

    override fun close() {
        if (!isOpen()) throw IllegalStateException("UDP socket is already closed")

        stopLanEstimation()

        bindJob?.cancel()
        bindJob = null

        tftpEndpoint.close()

        socket?.close()
        socket = null
    }

    open fun startLanEstimation() {
        lanEstimationJob =
            scope.launch {
                while (isActive) {
                    estimateLan()
                    delay(60_000)
                }
            }
    }

    private fun estimateLan() {
        val interfaces = NetworkInterface.getNetworkInterfaces()
        for (intf in interfaces) {
            for (intfAddr in intf.interfaceAddresses) {
                if (intfAddr.address is Inet4Address && !intfAddr.address.isLoopbackAddress) {
                    val estimatedAddress =
                        IPv4Address(intfAddr.address.hostAddress, getSocketPort())
                    setEstimatedLan(estimatedAddress)
                }
            }
        }
    }

    open fun stopLanEstimation() {
        lanEstimationJob?.cancel()
        lanEstimationJob = null
    }

    fun getSocketPort(): Int {
        return socket?.localPort ?: port
    }

    private fun bindSocket(socket: DatagramSocket) =
        scope.launch {
            try {
                val receiveData = ByteArray(UDP_PAYLOAD_LIMIT)
                while (isActive) {
                    val receivePacket = DatagramPacket(receiveData, receiveData.size)
                    withContext(Dispatchers.IO) {
                        socket.receive(receivePacket)
                    }
                    handleReceivedPacket(receivePacket)
                }
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }

    internal fun handleReceivedPacket(receivePacket: DatagramPacket) {
        logger.debug(
            "Received packet (${receivePacket.length} B) from " +
                "${receivePacket.address.hostAddress}:${receivePacket.port}",
        )

        // Check whether prefix is IPv8 or TFTP
        when (receivePacket.data[0]) {
            Community.PREFIX_IPV8 -> {
                val sourceAddress =
                    IPv4Address(receivePacket.address.hostAddress, receivePacket.port)
                val packet =
                    Packet(sourceAddress, receivePacket.data.copyOf(receivePacket.length))
                logger.debug(
                    "Received UDP packet (${receivePacket.length} B) from $sourceAddress",
                )

                notifyListeners(packet)
            }
            TFTPEndpoint.PREFIX_TFTP -> {
                tftpEndpoint.onPacket(receivePacket)
            }
            else -> {
                logger.warn { "Invalid packet prefix" }
            }
        }
    }

    companion object {
        // 1500 - 20 (IPv4 header) - 8 (UDP header)
        const val UDP_PAYLOAD_LIMIT = 1472
    }
}
