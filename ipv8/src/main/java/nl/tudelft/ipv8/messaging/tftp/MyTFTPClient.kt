package nl.tudelft.ipv8.messaging.tftp

import kotlinx.coroutines.sync.Mutex
import mu.KotlinLogging
import org.apache.commons.net.tftp.*
import java.io.IOException
import java.io.InputStream
import java.io.InterruptedIOException
import java.net.InetAddress
import java.net.SocketException

private val logger = KotlinLogging.logger {}

class MyTFTPClient : TFTPClient() {
    companion object {
        /***
         * The size to use for TFTP packet buffers. It's 4 + TFTPPacket.SEGMENT_SIZE, i.e. 516.
         */
        private const val PACKET_SIZE = TFTPPacket.SEGMENT_SIZE + 4
    }

    private var _totalBytesSent = 0L
    private val _sendBuffer: ByteArray = ByteArray(PACKET_SIZE)

    override fun sendFile(
        filename: String,
        mode: Int,
        input: InputStream,
        host: InetAddress,
        port: Int
    ) = synchronized(this) {
        var block = 0
        var hostPort = 0
        var justStarted = true
        var lastAckWait = false

        _totalBytesSent = 0L

        var sent: TFTPPacket = TFTPWriteRequestPacket(host, port, filename, mode)
        val data = TFTPDataPacket(host, port, 0, _sendBuffer, 4, 0)

        beginBufferedOps()

        try {
            do { // until eof
                // first time: block is 0, lastBlock is 0, send a request packet.
                // subsequent: block is integer starting at 1, send data packet.
                bufferedSend(sent)
                var wantReply = true
                var timeouts = 0
                do {
                    try {
                        logger.debug { "Waiting for receive..." }

                        val received = receive()

                        logger.debug { "Received TFTP packet of type ${received.type}" }

                        val recdAddress = received.address
                        val recdPort = received.port
                        // The first time we receive we get the port number and
                        // answering host address (for hosts with multiple IPs)
                        if (justStarted) {
                            justStarted = false
                            /*
                            if (recdPort == port) { // must not use the control port here
                                val error = TFTPErrorPacket(
                                    recdAddress,
                                    recdPort, TFTPErrorPacket.UNKNOWN_TID,
                                    "INCORRECT SOURCE PORT"
                                )
                                bufferedSend(error)
                                throw IOException("Incorrect source port ($recdPort) in request reply.")
                            }
                             */
                            hostPort = recdPort
                            data.port = hostPort
                            /*
                            if (host != recdAddress) {
                                host = recdAddress
                                data.address = host
                                sent.address = host
                            }
                             */
                        }
                        // Comply with RFC 783 indication that an error acknowledgment
                        // should be sent to originator if unexpected TID or host.
                        if (host == recdAddress && recdPort == hostPort) {
                            when (received.type) {
                                TFTPPacket.ERROR -> {
                                    val error = received as TFTPErrorPacket
                                    throw IOException(
                                        "Error code " + error.error +
                                            " received: " + error.message
                                    )
                                }
                                TFTPPacket.ACKNOWLEDGEMENT -> {
                                    val lastBlock =
                                        (received as TFTPAckPacket).blockNumber
                                    logger.debug { "ACK block: $lastBlock, expected: $block" }
                                    if (lastBlock == block) {
                                        ++block
                                        if (block > 65535) {
                                            // wrap the block number
                                            block = 0
                                        }
                                        wantReply = false // got the ack we want
                                    } else {
                                        logger.debug { "discardPackets" }
                                        discardPackets()
                                    }
                                }
                                else -> throw IOException("Received unexpected packet type.")
                            }
                        } else { // wrong host or TID; send error
                            val error = TFTPErrorPacket(
                                recdAddress,
                                recdPort,
                                TFTPErrorPacket.UNKNOWN_TID,
                                "Unexpected host or port."
                            )
                            bufferedSend(error)
                        }
                    } catch (e: SocketException) {
                        if (++timeouts >= DEFAULT_MAX_TIMEOUTS) {
                            throw IOException("Connection timed out.")
                        }
                    } catch (e: InterruptedIOException) {
                        if (++timeouts >= DEFAULT_MAX_TIMEOUTS) {
                            throw IOException("Connection timed out.")
                        }
                    } catch (e: TFTPPacketException) {
                        throw IOException("Bad packet: " + e.message)
                    }
                    // retry until a good ack
                } while (wantReply)
                if (lastAckWait) {
                    break // we were waiting for this; now all done
                }
                var dataLength = TFTPPacket.SEGMENT_SIZE
                var offset = 4
                var totalThisPacket = 0
                var bytesRead = 0
                while (dataLength > 0 &&
                    input.read(_sendBuffer, offset, dataLength).also { bytesRead = it } > 0
                ) {
                    offset += bytesRead
                    dataLength -= bytesRead
                    totalThisPacket += bytesRead
                }
                if (totalThisPacket < TFTPPacket.SEGMENT_SIZE) {
                    /* this will be our last packet -- send, wait for ack, stop */
                    lastAckWait = true
                }
                data.blockNumber = block
                data.setData(_sendBuffer, 4, totalThisPacket)
                sent = data
                _totalBytesSent += totalThisPacket.toLong()
            } while (true) // loops until after lastAckWait is set
        } finally {
            endBufferedOps()
        }
        logger.debug { "sendFile finished" }
    }
}
