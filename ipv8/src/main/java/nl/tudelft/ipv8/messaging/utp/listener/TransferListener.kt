package nl.tudelft.ipv8.messaging.utp.listener

import net.utp4j.channels.futures.UtpReadListener

abstract class TransferListener : UtpReadListener() {

    abstract val queue: ArrayDeque<ByteArray>

//    fun onTransferComplete(): ByteArray

}
