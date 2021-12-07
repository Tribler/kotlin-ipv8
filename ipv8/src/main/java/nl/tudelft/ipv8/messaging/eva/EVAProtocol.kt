package nl.tudelft.ipv8.messaging.eva

import mu.KotlinLogging
import nl.tudelft.ipv8.Community
import nl.tudelft.ipv8.Peer
import nl.tudelft.ipv8.messaging.payload.EVAWriteRequestPayload
import java.util.*

private val logger = KotlinLogging.logger {}

class EVAProtocol(
    val community: Community,
    val scope: CoroutineScope
) {

    private lateinit var onEVAReceiveProgressCallback: (peer: Peer, info: String, progress: TransferProgress) -> Unit
    private lateinit var onEVAReceiveCompleteCallback: (peer: Peer, info: String, id: String, dataBinary: ByteArray?) -> Unit
    private lateinit var onEVASendCompleteCallback: (peer: Peer, info: String, dataBinary: ByteArray?, nonce: ULong) -> Unit
    private lateinit var onEVAErrorCallback: (peer: Peer, exception: TransferException) -> Unit
    private var incoming: MutableList<Key, Transfer> = mutableListOf()
    private var outgoing: MutableList<Key, Transfer> = mutableListOf()
    private var scheduled: MutableList<Key, PriorityQueue<ScheduledTransfer>> = mutableListOf()

    private var scheduledTasks: MutableList<ScheduledTask>

    var terminateByTimeoutEnabled = true

    init {
        logger.debug {

        }
    }

    fun MutableList<K, V>.putValue(key: K, value: V) {

    }

    fun sendBinary(peer: Peer, info: String, id: String, data: ByteArray, nonce: Long? = null) {

        if (data == null || peer == community.myPeer) return

        val infoBinary = info.toByteArray(Charsets.UTF_8)

        val nonceValue = nonce ?: (0..MAX_NONCE_VALUE).random()

        if (peer.key in outgoing) {
            val scheduledTransfer = ScheduledTransfer(infoBinary, dataBinary, nonce)
            val transfer = Transfer(TransferType.OUTGOING, scheduledTransfer)
            scheduled.putValue(peer.key, transfer)
        }

        startOutgoingTransfer(peer, infoBinary, dataBinary, nonce)

    }

    fun registerTask() {

    }


    fun onWriteRequest(peer: Peer, payload: EVAWriteRequestPayload) {

    }

    fun onEVAAcknowledgement(peer: Peer, payload: EVAAcknowledgementPayload) {


//        ra = blockNumber..(blockerNumber + windowSize - 1)
        //	for (blockNumber in ra) {
//        if (blockNumber > dataSize -1) return
//        val startPosition = blockNumber * blockSize
//        val stopPosition = startPosition + blockSize
//       val dat = byteArray.copyOfRange(startPosition, stopPosition)
//        println("$blockNumber: ${dat.contentToString()}")
//    }


    }

    fun onEVAData(peer: Peer, payload: EVADataPayload) {

    }

    fun onEVAError(peer: peer, exception: TransferException) {

    }

    fun sendScheduled() {

    }

    companion object {
        const val MAX_NONCE_VALUE = (Int.MAX_VALUE).toLong() * 2
        const val BLOCK_SIZE = 1000
        const val WINDOW_SIZE_IN_BLOCKS = 64
        const val START_MESSAGE_ID = 186
        const val SCHEDULED_SEND_INTERVAL_IN_SEC = 5
        const val RETRANSMIT_INTERVAL_IN_SEC = 3
        const val RETRANSMIT_ATTEMPT_COUNT = 3
        const val TIMEOUT_INTERVAL_IN_SEC = 10
        const val BINARY_SIZE_LIMIT = 1024 * 1024 * 1024
    }
}
