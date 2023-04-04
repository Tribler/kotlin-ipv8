package nl.tudelft.ipv8.messaging.eva

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import mu.KotlinLogging
import nl.tudelft.ipv8.Community
import nl.tudelft.ipv8.Peer
import nl.tudelft.ipv8.keyvault.Key
import nl.tudelft.ipv8.util.toHex
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.*

private val logger = KotlinLogging.logger {}

open class EVAProtocol(
    private var community: Community,
    private val scope: CoroutineScope,
    var blockSize: Int = BLOCK_SIZE,
    var windowSize: Int = WINDOW_SIZE,
    var binarySizeLimit: Int = BINARY_SIZE_LIMIT,
    var retransmitEnabled: Boolean = true,
    var retransmitInterval: Long = RETRANSMIT_INTERVAL,
    var retransmitAttemptCount: Int = RETRANSMIT_ATTEMPT_COUNT,
    var scheduledSendInterval: Long = SCHEDULED_SEND_INTERVAL,
    var scheduledTasksCheckInterval: Long = SCHEDULED_TASKS_CHECK_DELAY,
    var terminateByTimeoutEnabled: Boolean = true,
    var timeoutInterval: Long = TIMEOUT_INTERVAL,
    var reduceWindowAfterTimeout: Int = REDUCE_WINDOW_AFTER_TIMEOUT,
    var loggingEnabled: Boolean = true,
    private val maxSimultaneousTransfers: Int = 10
) {
    private val mutex = Mutex()

    private var scheduled: MutableMap<Key, Queue<ScheduledTransfer>> = mutableMapOf()
    private var incoming: MutableMap<Key, Transfer> = mutableMapOf()
    private var outgoing: MutableMap<Key, Transfer> = mutableMapOf()
    private var stoppedIncoming: MutableMap<Key, MutableSet<String>> = mutableMapOf()
    private var finishedIncoming: MutableMap<Key, MutableSet<String>> = mutableMapOf()
    private var finishedOutgoing: MutableMap<Key, MutableSet<String>> = mutableMapOf()
    private var timedOutOutgoing: HashMap<String, Int> = HashMap()
    private var scheduledTasks = PriorityQueue<ScheduledTask>()

    var onReceiveProgressCallback: ((Peer, String, TransferProgress) -> Unit)? = null
    var onReceiveCompleteCallback: ((Peer, String, String, ByteArray?) -> Unit)? = null
    var onSendCompleteCallback: ((Peer, String, ULong) -> Unit)? = null
    var onErrorCallback: ((Peer, TransferException) -> Unit)? = null

    init {
        if (loggingEnabled) logger.debug {
            "EVAPROTOCOL: Initialized. " +
                "Block size: ${blockSize}." +
                "Window size: ${windowSize}." +
                "Binary size limit: ${binarySizeLimit}." +
                "Retransmit enabled: ${retransmitEnabled}." +
                "Retransmit interval: ${retransmitInterval}millis." +
                "Max retransmit attempts: ${retransmitAttemptCount}." +
                "Scheduled send interval: ${scheduledSendInterval}millis." +
                "Scheduled tasks check interval: ${scheduledTasksCheckInterval}millis." +
                "Terminate by timeout enabled: ${terminateByTimeoutEnabled}." +
                "Timeout interval: ${timeoutInterval}millis." +
                "Reduce window after timeout: ${reduceWindowAfterTimeout}." +
                "Logging enabled: $loggingEnabled."
        }

        /**
         * Coroutine that periodically executes the send scheduled transfers function
         * with a send interval as the delay
         */
        scope.launch {
            while (isActive) {
                sendScheduled()

                delay(scheduledSendInterval)
            }
        }

        /**
         * Coroutine that periodically checks whether there are scheduled tasks planned in the queue
         * and if so, if its time to be executed. The interval is set to 1 second.
         */
        scope.launch {
            while (isActive) {
                while (scheduledTasks.any() && scheduledTasks.peek() != null && scheduledTasks.peek()!!.atTime <= Date().time) {
                    val task = scheduledTasks.poll()!!

                    task.action.invoke()
                }

                delay(scheduledTasksCheckInterval)
            }
        }
    }

    /**
     * Get peer if it is connected to the network
     * @param key the key of the peer
     * @return the requested peer or null if not connected
     */
    private fun getConnectedPeer(key: Key): Peer? {
        return community.getPeers().firstOrNull {
            it.key == key
        }
    }

    /**
     * Register an anonymous task to be executed at a particular time
     *
     * @param atTime the time of execution
     * @param task the task/action that must be executed
     */
    private fun scheduleTask(atTime: Long, task: () -> Unit) {
        scheduledTasks.add(
            ScheduledTask(
                atTime,
                task
            )
        )
    }

    /**
     * Send an EVA packet over an endpoint defined in the community
     *
     * @param peer the peer to send to
     * @param packet the packet to send to the peer
     */
    private fun send(peer: Peer, packet: ByteArray) {
        community.endpoint.send(peer, packet)
    }

    /**
     * Check if the transfer of an outgoing file is already scheduled
     *
     * @param key the key of the receiver
     * @param id an id of the file
     */
    private fun isScheduled(key: Key, id: String): Boolean {
        return scheduled.containsKey(key) && scheduled[key]!!.any {
            it.id == id
        }
    }

    /**
     * Check if the transfer of an outgoing file has already started
     *
     * @param key the key of the receiver
     * @param id an id of the file
     */
    private fun isOutgoing(key: Key, id: String): Boolean {
        return outgoing.any {
            it.key == key && it.value.id == id
        }
    }

    /**
     * Check if the transfer of an incoming file has already started
     *
     * @param key the key of the sender
     * @param id an id of the file
     */
    private fun isIncoming(key: Key, id: String): Boolean {
        return incoming.any {
            it.key == key && it.value.id == id
        }
    }

    /**
     * Check if the transfer of an incoming file has been stopped
     *
     * @param key the key of sender
     * @param id an id of the file
     */
    fun isStopped(key: Key, id: String): Boolean {
        return stoppedIncoming.any {
            it.key == key && it.value.contains(id)
        }
    }

    /**
     * Check if the transfer of an outgoing or incoming file has already been transferred
     *
     * @param key the key of the receiver
     * @param id an id of the file
     * @param container incoming or outgoing finished set containing sent/received file id's
     */
    private fun isTransferred(
        key: Key,
        id: String,
        container: MutableMap<Key, MutableSet<String>>
    ): Boolean {
        return if (container.containsKey(key)) {
            container[key]!!.contains(id)
        } else false
    }

    /**
     * Lookup the number of timeouts for a particular file transfer to a particular peer]
     *
     * @param key the key of a peer
     * @param id the file ID
     * @return the count of timeouts, otherwise 0
     */
    private fun getTimedOutCount(key: Key, id: String): Int {
        return timedOutOutgoing[key.toString() + id] ?: 0
    }

    /**
     * Determine the window size, including a reduction factor if timeout(s) happened before. In situations
     * where the connection is bad or suddenly dropped it may be profitable to use a lower window.
     * For every timeout that happened for a particular file transfer the window size is dropped
     * by default 16 blocks.
     *
     * @param key the key of a peer
     * @param id the file ID
     * @return the window size in blocks
     */
    private fun getWindowSize(key: Key, id: String): Int {
        return kotlin.math.max(MIN_WINDOW_SIZE, windowSize - (getTimedOutCount(key, id) * reduceWindowAfterTimeout))
    }

    /**
     * Entrypoint to send binary data using the EVA protocol.
     *
     * @param peer the address to deliver the data
     * @param info string that identifies to which communitu or class it should be delivered
     * @param id file/data identifier that identifies the sent data
     * @param data serialized packet in bytes
     * @param nonce an optional unique number that identifies this transfer
     */
    fun sendBinary(
        peer: Peer,
        info: String,
        id: String,
        data: ByteArray,
        nonce: Long? = null
    ) {
        if (info.isEmpty() || id.isEmpty() || data.isEmpty() || peer == community.myPeer) {
            return
        }

        // Stop if a transfer of the requested file is already scheduled, outgoing or transferred
        if (isScheduled(peer.key, id) || isOutgoing(peer.key, id) || isTransferred(
                peer.key,
                id,
                finishedOutgoing
            )
        ) {
            return
        }

        val nonceValue = (nonce ?: (0..MAX_NONCE).random())

        if (loggingEnabled) logger.debug { "EVAPROTOCOL: Sending binary with id '$id', nonce '$nonceValue' for info '$info'." }

        if (outgoing.containsKey(peer.key) || incoming.containsKey(peer.key) || getConnectedPeer(peer.key) == null || isSimultaneouslyServedTransfersLimitExceeded()) {
            if (!isScheduled(peer.key, id)) {
                val windowSize = getWindowSize(peer.key, id)
                scheduled.addValue(
                    peer.key,
                    ScheduledTransfer(
                        info,
                        data,
                        nonceValue.toULong(),
                        id,
                        0,
                        data.size.toULong(),
                        blockSize,
                        windowSize
                    )
                )

                onReceiveProgressCallback?.invoke(
                    peer, info, TransferProgress(
                        id,
                        TransferState.SCHEDULED,
                        0.0
                    )
                )
            }

            return
        }

        startOutgoingTransfer(peer, info, id, data, nonceValue)
    }

    /**
     * This asserts an upper limit of simultaneously served peers.
     * The reason for introducing this parameter is to have a tool for
     * limiting socket load which could lead to packet loss.
     */
    private fun isSimultaneouslyServedTransfersLimitExceeded(): Boolean {
        val transfersCount = incoming.size + outgoing.size
        return transfersCount >= maxSimultaneousTransfers
    }

    /**
     * Start an outgoing transfer of data
     *
     * @param peer the receiver of the data
     * @param info a string that defines the requested class/community
     * @param id unique file/data identifier that enables identification on sender and receiver side
     * @param data the data in bytes
     * @param nonce an unique number that defines this transfer
     */
    private fun startOutgoingTransfer(peer: Peer, info: String, id: String, data: ByteArray, nonce: Long) {
        if (loggingEnabled) logger.debug { "EVAPROTOCOL: Start outgoing transfer." }

        val scheduledTransfer = ScheduledTransfer(
            info,
            byteArrayOf(),
            nonce.toULong(),
            id,
            BigDecimal(data.size).divide(BigDecimal(blockSize), RoundingMode.UP).toInt(),
            data.size.toULong(),
            blockSize,
            getWindowSize(peer.key, id)
        )

        if (getConnectedPeer(peer.key) == null || isOutgoing(peer.key, id) ||
            isTransferred(peer.key, id, finishedOutgoing) || outgoing.containsKey(peer.key) ||
            incoming.containsKey(peer.key)
        ) {
            if (!isScheduled(peer.key, id)) {
                scheduled.addValue(peer.key, scheduledTransfer)

                onReceiveProgressCallback?.invoke(
                    peer, info, TransferProgress(
                        id,
                        TransferState.SCHEDULED,
                        0.0
                    )
                )
            }

            return
        }

        val transfer = Transfer(
            TransferType.OUTGOING,
            scheduled[peer.key]?.firstOrNull { it.id == id } ?: scheduledTransfer
        )

        if (loggingEnabled) logger.debug { "EVAPROTOCOL: Outgoing transfer blockCount: ${transfer.blockCount}, size: ${transfer.dataSize}, nonce: ${transfer.nonce}, window: ${transfer.windowSize}" }

        if (transfer.dataSize.toLong() > binarySizeLimit) {
            notifyError(
                peer,
                SizeException(
                    "Current data size limit(${binarySizeLimit}) has been exceeded",
                    info,
                    transfer
                )
            )
            return
        }

        transfer.data = data

        outgoing[peer.key] = transfer
        scheduleTerminate(outgoing, peer, transfer)

        sendWriteRequest(peer, transfer)
        retryWriteRequestIfNeeded(peer, transfer)
    }

    private fun retryWriteRequestIfNeeded(
        peer: Peer,
        transfer: Transfer
    ) {
        scope.launch {
            if (retransmitEnabled) {
                for (attempt in 1..retransmitAttemptCount) {
                    delay(retransmitInterval)
                    if (transfer.released || transfer.ackedWindow != 0) {
                        return@launch
                    }

                    val currentAttempt = "$attempt/$retransmitAttemptCount"
                    if (loggingEnabled) logger.debug { "EVAPROTOCOL: Retrying Write Request. Attempt $currentAttempt for peer: $peer" }
                    sendWriteRequest(peer, transfer)
                }
            }
        }
    }

    private fun sendWriteRequest(
        peer: Peer,
        transfer: Transfer
    ) {
        val writeRequestPacket = community.createEVAWriteRequest(
            peer,
            transfer.info,
            transfer.id,
            transfer.nonce,
            transfer.dataSize,
            transfer.blockCount.toUInt(),
            transfer.blockSize.toUInt(),
            transfer.windowSize.toUInt()
        )
        send(peer, writeRequestPacket)
    }

    /**
     * Upon receipt of an EVA write request the incoming transfer is announced and acknowledged
     *
     * @param peer the sender of the write request
     * @param payload information about the coming transfer (size, blocks, nonce, id, class)
     */
    fun onWriteRequest(peer: Peer, payload: EVAWriteRequestPayload) {
        if (loggingEnabled) logger.debug {
            "EVAPROTOCOL: On write request. Nonce: ${payload.nonce}. Peer: ${
                peer.publicKey.keyToBin().toHex()
            }. Info: ${payload.info}. BlockCount: ${payload.blockCount}. Blocksize: ${payload.blockSize.toInt()}. Window size: ${payload.windowSize.toInt()}. Payload datasize: ${payload.dataSize}, allowed datasize: $binarySizeLimit"
        }

        if (isIncoming(peer.key, payload.id) || isTransferred(
                peer.key,
                payload.id,
                finishedIncoming
            ) || isStopped(peer.key, payload.id)
        ) {
            return
        }

        val scheduledTransfer = ScheduledTransfer(
            payload.info,
            byteArrayOf(),
            payload.nonce,
            payload.id,
            payload.blockCount.toInt(),
            payload.dataSize,
            payload.blockSize.toInt(),
            payload.windowSize.toInt()
        )
        val transfer = Transfer(TransferType.INCOMING, scheduledTransfer)

        if (loggingEnabled) logger.debug { "EVAPROTOCOL: $transfer" }

        when {
            payload.dataSize <= 0u -> {
                incomingError(
                    peer,
                    transfer,
                    SizeException(
                        "Data size can not be less or equal to 0",
                        payload.info,
                        transfer
                    )
                )
                return
            }

            payload.dataSize.toInt() > binarySizeLimit -> {
                incomingError(
                    peer,
                    transfer,
                    SizeException(
                        "Current data size limit($binarySizeLimit) has been exceeded",
                        payload.info,
                        transfer
                    )
                )
                return
            }
        }

        transfer.dataSize = payload.dataSize
        transfer.attempt = 0

        if (incoming.contains(peer.key) || outgoing.contains(peer.key)) {
            if (loggingEnabled) logger.debug { "EVAPROTOCOL: There is already a transfer ongoing with peer" }

            incomingError(
                peer,
                transfer,
                PeerBusyException(
                    "There is already a transfer ongoing with peer",
                    payload.info,
                    transfer
                )
            )
            return
        }

        incoming[peer.key] = transfer

        sendAcknowledgement(peer, transfer)
        scheduleTerminate(incoming, peer, transfer)
        scheduleResendAcknowledge(peer, transfer)
    }

    /**
     * Upon receipt of an EVA acknowledgement the blocks are sent in windows. On acknowledgement of
     * the previous window the blocks in the next window are transmitted.
     *
     * @param peer the sender of the acknowledgement
     * @param payload acknowledgement of sent blocks with info about number, windowsize and nonce
     */
    fun onAcknowledgement(peer: Peer, payload: EVAAcknowledgementPayload) {
        if (loggingEnabled) logger.debug { "EVAPROTOCOL: On acknowledgement for window '${payload.ackWindow}'." }

        val transfer = outgoing[peer.key] ?: return
        if (loggingEnabled) logger.debug { "EVAPROTOCOL: Transfer: $transfer" }

        if (isStopped(peer.key, transfer.id)) {
            return
        }

        if (transfer.nonce != payload.nonce) {
            return
        }

        transfer.updated = Date().time

        if (payload.ackWindow.toInt() > 0) {
            if (loggingEnabled) logger.debug { "EVAPROTOCOL: UNACKED ${payload.unAckedBlocks.toString(Charsets.UTF_8)}" }
            transfer.addUnreceivedBlocks(payload.unAckedBlocks)
        }

        transfer.ackedWindow = payload.ackWindow.toInt()

        transfer.setSendSet().let {
            if (it.isEmpty()) {
                finishOutgoingTransfer(peer, transfer)
                return
            } else {
                transmitData(peer, transfer)
                transfer.unReceivedBlocks = mutableSetOf()
            }
        }
    }

    private fun transmitData(peer: Peer, transfer: Transfer) {
        transfer.lastSentBlocks.forEach { blockNumber ->
            if (blockNumber > transfer.blockCount - 1) {
                return
            }

            val data = transfer.getData(blockNumber)
            if (data.isEmpty()) {
                return
            }

            if (loggingEnabled) {
                logger.debug { "EVAPROTOCOL: Transmit($blockNumber/${transfer.blockCount - 1})" }
            }

            val dataPacket = community.createEVAData(peer, blockNumber.toUInt(), transfer.nonce, data)
            send(peer, dataPacket)
        }
    }

    /**
     * Upon receipt of an EVA data block, the binary data is added to the already received data.
     * An acknowledgement is sent when the block is the last of its window.
     *
     * @param peer the sender of the data block
     * @param payload data block consisting of blocknumber, nonce and binary data
     */
    fun onData(peer: Peer, payload: EVADataPayload) {
        if (loggingEnabled) logger.debug { "EVAPROTOCOL: On data(${payload.blockNumber}). Nonce ${payload.nonce}. Peer hash: ${peer.mid}." }

        val transfer = incoming[peer.key] ?: return

        if (isStopped(peer.key, transfer.id)) {
            return
        }

        if (transfer.nonce != payload.nonce) {
            return
        }

        val blockNumber = payload.blockNumber.toInt()

        when (transfer.ackedWindow) {
            0 -> {
                TransferProgress(transfer.id, TransferState.INITIALIZING, 0.0)
            }

            else -> {
                TransferProgress(transfer.id, TransferState.DOWNLOADING, transfer.getProgress())
            }
        }.let {
            onReceiveProgressCallback?.invoke(peer, transfer.info, it)
        }

        if (!transfer.isBlockReceived(blockNumber)) {
            transfer.addData(blockNumber, payload.data)
        }
        transfer.attempt = 0
        transfer.updated = Date().time

        if (transfer.unReceivedBlocks.size == 0) {
            if (loggingEnabled) logger.debug { "EVAPROTOCOL: Final data packet received" }
            transfer.ackedWindow += 1
            sendAcknowledgement(peer, transfer)
            finishIncomingTransfer(peer, transfer)
            return
        }

        val timeToAcknowledge = blockNumber == kotlin.math.min(
            (transfer.ackedWindow + 1) * transfer.windowSize - 1,
            transfer.blockCount - 1
        )
        if (timeToAcknowledge) {
            if (blockNumber < (transfer.ackedWindow + 1) * transfer.windowSize) {
                transfer.ackedWindow += 1
            }
            sendAcknowledgement(peer, transfer)
        }
    }

    /**
     * Upon receipt of an EVA error block the error is notified to the protocol and the outgoing
     * transfer is terminated and error callbacks are fired.
     *
     * @param peer the sender of the error block
     * @param payload contains the error message
     */
    fun onError(peer: Peer, payload: EVAErrorPayload) {
        if (loggingEnabled) logger.debug { "EVAPROTOCOL: On error with message: ${payload.message}" }

        val transfer = outgoing[peer.key] ?: return
        terminate(outgoing, peer, transfer)

        notifyError(
            peer,
            TransferException(
                payload.message,
                payload.info,
                transfer
            )
        )

        scope.launch {
            sendScheduled()
        }
    }

    /**
     * The process of sending an acknowledgement block
     *
     * @param peer the receiver of the acknowledgement
     * @param transfer the corresponding transfer object for the acknowledgement
     */
    private fun sendAcknowledgement(peer: Peer, transfer: Transfer) {
        val unReceivedBlocks = if (transfer.ackedWindow > 0) {
            transfer.getUnreceivedBlocksUntil()
        } else byteArrayOf()

        if (loggingEnabled) logger.debug { "EVAPROTOCOL: Acknowledgement for window '${transfer.ackedWindow}' sent to ${peer.mid} with windowSize (${transfer.windowSize}) and nonce ${transfer.nonce}" }

        val ackPacket = community.createEVAAcknowledgement(
            peer,
            transfer.nonce,
            transfer.ackedWindow.toUInt(),
            unReceivedBlocks
        )
        send(peer, ackPacket)
    }

    /**
     * Finishing an incoming transfer and callbacks to its listeners
     *
     * @param peer sender of the transfer
     * @param transfer corresponding transfer
     */
    private fun finishIncomingTransfer(peer: Peer, transfer: Transfer) {
        val data = transfer.data
        val info = transfer.info

        if (loggingEnabled) logger.debug { "EVAPROTOCOL: Incoming transfer finished: id ${transfer.id} and info $info" }

        finishedIncoming.add(peer.key, transfer.id)
        terminate(incoming, peer, transfer)

        onReceiveProgressCallback?.invoke(
            peer, info, TransferProgress(
                transfer.id,
                TransferState.FINISHED,
                100.0
            )
        )

        onReceiveCompleteCallback?.invoke(peer, info, transfer.id, data)
    }

    /**
     * Finishing an outgoing transfer and callbacks to its listeners
     *
     * @param peer sender of the transfer
     * @param transfer corresponding transfer
     */
    private fun finishOutgoingTransfer(peer: Peer, transfer: Transfer) {
        val info = transfer.info
        val nonce = transfer.nonce

        if (loggingEnabled) logger.debug { "EVAPROTOCOL: Outgoing transfer finished: id: ${transfer.id}, nonce: ${transfer.nonce} and info $info" }

        finishedOutgoing.add(peer.key, transfer.id)
        timedOutOutgoing.remove(peer.key.toString() + transfer.id)
        terminate(outgoing, peer, transfer)

        if (loggingEnabled) logger.debug { "EVAPROTOCOL: Timeout map AFTER TRANSFER: $timedOutOutgoing" }

        onSendCompleteCallback?.invoke(peer, info, nonce)

        scope.launch {
            sendScheduled()
        }
    }

    /**
     * Send the next scheduled transfer
     */
    private suspend fun sendScheduled() {
        mutex.withLock {
            if (isSimultaneouslyServedTransfersLimitExceeded()) {
                return
            }

            val idlePeerKeys = scheduled
                .filter { !outgoing.contains(it.key) }
                .map { it.key }

            for (key in idlePeerKeys) {
                val peer = getConnectedPeer(key) ?: continue

                if (!scheduled.containsKey(key)) continue

                scheduled.popValue(key)?.let { transfer ->
                    startOutgoingTransfer(
                        peer,
                        transfer.info,
                        transfer.id,
                        transfer.data,
                        transfer.nonce.toLong(),
                    )
                }
            }
        }
    }

    /**
     * Terminate a particular transfer by releasing it and removing it from the in/out set
     *
     * @param container incoming or outgoing set
     * @param peer transfer with peer
     * @param transfer corresponding transfer
     */
    private fun terminate(container: MutableMap<Key, Transfer>, peer: Peer, transfer: Transfer) {
        if (loggingEnabled) logger.debug { "EVAPROTOCOL: Transfer terminated: $transfer" }

        transfer.release()
        container.remove(peer.key)
    }

    /**
     * Create and send an error during an incoming transfer
     *
     * @param peer sender of block
     * @param transfer (optional) corresponding transfer object
     * @param exception transfer exception
     */
    private fun incomingError(peer: Peer, transfer: Transfer, exception: TransferException) {
        terminate(incoming, peer, transfer)

        val errorPacket = community.createEVAError(peer, exception.info, exception.localizedMessage ?: "Unknown error")
        send(peer, errorPacket)

        notifyError(peer, exception)
    }

    /**
     * Callbacks for the errors during transfer
     *
     * @param peer transfer with peer
     * @param exception transfer exception
     */
    private fun notifyError(peer: Peer, exception: TransferException) {
        if (loggingEnabled) logger.debug { "EVAPROTOCOL ${exception.m} ${exception.info} ${exception.transfer}" }
        onErrorCallback?.invoke(peer, exception)
    }

    /**
     * Schedule to terminate transfer (if enabled) after a predefined timeout interval has passed.
     *
     * @param container transfer in incoming/outgoing set of transfers
     * @param peer the sender/receiver
     * @param transfer transfer to be terminated
     */
    private fun scheduleTerminate(container: MutableMap<Key, Transfer>, peer: Peer, transfer: Transfer) {
        if (!terminateByTimeoutEnabled) {
            return
        }

        if (loggingEnabled) logger.debug { "EVAPROTOCOL: Schedule terminate for transfer with id ${transfer.id} and nonce ${transfer.nonce}" }

        scheduleTask(Date().time + timeoutInterval) {
            terminateByTimeout(container, peer, transfer)
        }
    }

    /**
     * Terminate an transfer when timeout passed and no remaining time left or schedule new timeout
     * task to check the timeout conditions. If the transfer is outgoing and there's no remaining
     * time, the window size will be lowered in the next transfer.
     *
     * @param container transfer in incoming/outgoing set of transfers
     * @param peer the sender/receiver
     * @param transfer transfer to be terminated
     */
    private fun terminateByTimeout(container: MutableMap<Key, Transfer>, peer: Peer, transfer: Transfer) {
        if (loggingEnabled) logger.debug { "EVAPROTOCOL: Scheduled terminate task executed. Transfer is released: ${transfer.released}" }

        if (transfer.released || !terminateByTimeoutEnabled) {
            return
        }

        val remainingTime = timeoutInterval - (Date().time - transfer.updated)

        if (remainingTime > 0) {
            scheduleTask(Date().time + remainingTime) {
                terminateByTimeout(container, peer, transfer)
            }
            return
        }

        terminate(container, peer, transfer)

        if (transfer.type == TransferType.OUTGOING) {
            if (loggingEnabled) logger.debug {
                "EVAPROTOCOL: Incrementing timedOutOutgoing count for transfer to ${
                    getTimedOutCount(
                        peer.key,
                        transfer.id
                    )
                }."
            }
            timedOutOutgoing.increment(peer.key.toString() + transfer.id)
        }

        notifyError(
            peer,
            TimeoutException(
                "Terminated by timeout. Timeout is ${timeoutInterval / 1000} sec",
                transfer.info,
                transfer
            )
        )
    }

    /**
     * Schedule the resend of an acknowledge
     *
     * @param peer the receiver of the acknowledgement
     * @param transfer corresponding transfer
     */
    private fun scheduleResendAcknowledge(peer: Peer, transfer: Transfer) {
        if (!retransmitEnabled) {
            return
        }

        scheduleTask(Date().time + retransmitInterval) {
            resendAcknowledge(peer, transfer)
        }
    }

    /**
     * Resend acknowledge to peer of transfer when the transfer has not been released (yet) and
     * there are still retransmit attempts left. Resend is needed when the time between the last
     * received block and now is bigger than a predefined retransmit interval. In total a predefined
     * number of retransmits are allowed.
     *
     * @param peer the receiver of the acknowledgement
     * @param transfer corresponding transfer
     */
    private fun resendAcknowledge(peer: Peer, transfer: Transfer) {
        if (transfer.released || !retransmitEnabled || transfer.attempt >= retransmitAttemptCount - 1) {
            return
        }

        if (Date().time - transfer.updated >= retransmitInterval) {
            transfer.attempt += 1

            if (loggingEnabled) {
                logger.debug { "EVAPROTOCOL: Re-acknowledgement attempt ${transfer.attempt + 1}/$retransmitAttemptCount for window ${transfer.ackedWindow}." }
            }

            sendAcknowledgement(peer, transfer)
        }

        scheduleTask(Date().time + retransmitInterval) {
            resendAcknowledge(peer, transfer)
        }
    }

    /**
     * Toggle the state of an incoming transfer from downloading or scheduled to stopped,
     * and vice versa. This also returns the state to the requestor. The requestor simply calls
     * the function without knowing in what start state the transfer is, but will know the end state.
     *
     * @param peer the peer/sender of the transfer
     * @param id the id of the file
     * @return the state of the transfer
     */
    fun toggleIncomingTransfer(peer: Peer, id: String): TransferState {
        return if (stoppedIncoming.containsKey(peer.key) && stoppedIncoming[peer.key]!!.contains(id)) {
            stoppedIncoming[peer.key]!!.remove(id)
            TransferState.SCHEDULED
        } else {
            if (incoming.containsKey(peer.key)) {
                val transfer = incoming[peer.key]
                if (transfer != null && transfer.id == id) {
                    if (stopIncomingTransfer(peer, transfer)) {
                        TransferState.STOPPED
                    } else TransferState.UNKNOWN
                } else TransferState.UNKNOWN
            } else TransferState.UNKNOWN
        }
    }

    /**
     * Stops an incoming transfer by terminating the transfer in the incoming set and adding it to
     * the stoppedIncoming set. Returns whether the transfer is added to the stoppedIncoming set.
     *
     * @param peer the peer/sender of the transfer
     * @param transfer the transfer itself
     * @return a boolean indicating if thet transfer is stopped
     */
    private fun stopIncomingTransfer(peer: Peer, transfer: Transfer): Boolean {

        terminate(incoming, peer, transfer)
        stoppedIncoming.add(peer.key, transfer.id)

        return if (stoppedIncoming.containsKey(peer.key)) {
            stoppedIncoming[peer.key]!!.contains(transfer.id)
        } else false

    }

    companion object {
        const val MAX_NONCE = Integer.MAX_VALUE.toLong() * 2
        const val BLOCK_SIZE = 600
        const val WINDOW_SIZE = 80
        const val BINARY_SIZE_LIMIT = 1024 * 1024 * 250
        const val RETRANSMIT_INTERVAL = 2000L
        const val RETRANSMIT_ATTEMPT_COUNT = 3
        const val TIMEOUT_INTERVAL = 20000L
        const val REDUCE_WINDOW_AFTER_TIMEOUT = 16
        const val MIN_WINDOW_SIZE = 16
        const val SCHEDULED_SEND_INTERVAL = 5000L
        const val SCHEDULED_TASKS_CHECK_DELAY = 1000L
    }
}

/**
 * Add functionality for a mutable map in which the value contains a queue
 *
 * @property MutableMap the map consisting of a key and value (queue)
 * @param key the key in the map of type K
 * @param value the value, a queue, in the map of type V
 */
fun <K, V> MutableMap<K, Queue<V>>.addValue(key: K, value: V) {
    if (this.containsKey(key)) {
        this[key]?.add(value)
    } else {
        this[key] = LinkedList(mutableListOf(value))
    }
}

/**
 * Pop value in queue for a mutable map in which the value contains a queue
 *
 * @property MutableMap the map consisting of a key of type K and value (queue) of type V
 * @param key the key in the map of which the queue is the value
 * @return the popped value or null
 */
fun <K, V> MutableMap<K, Queue<V>>.popValue(key: K): V? {
    if (this.containsKey(key)) {
        val value = this[key]?.poll()

        if (this[key]?.isEmpty() == true) {
            this.remove(key)
        }

        return value
    }

    return null
}

/**
 * Add functionality for a mutable map in which the value contains a set
 *
 * @property MutableMap the map consisting of a key and value (mutable set)
 * @param key the key in the map of type K
 * @param value the value, a mutable set, in the map of type V
 */
fun <K, V> MutableMap<K, MutableSet<V>>.add(key: K, value: V) {
    if (this.containsKey(key)) {
        this[key]?.add(value)
    } else {
        this[key] = mutableSetOf(value)
    }
}

/**
 * Copy range of a bytearray, given from and to index, depending on size of array.
 *
 * @property ByteArray the byte array
 * @param fromIndex start index
 * @param toIndex end index, may be less if size of array is smaller
 * @return range of copied values from array
 */
fun ByteArray.takeInRange(fromIndex: Int, toIndex: Int): ByteArray {
    val to = if (toIndex > this.size) {
        this.size
    } else toIndex

    return this.copyOfRange(fromIndex, to)
}

/**
 * Increment an integer value in hashmap based on current value
 *
 * @property HashMap<K, Int> the map
 * @param key the key in the map
 */
fun <K> HashMap<K, Int>.increment(key: K) {
    when (val count = this[key]) {
        null -> this[key] = 1
        else -> this[key] = count + 1
    }
}
