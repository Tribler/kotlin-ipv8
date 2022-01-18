package nl.tudelft.ipv8.messaging.eva

open class TransferException(
    var m: String,
    var info: String,
    var transfer: Transfer? = null
) : Exception()

class SizeException(m: String, info: String, transfer: Transfer? = null) : TransferException(m, info, transfer)

class TimeoutException(m: String, info: String, transfer: Transfer? = null) : TransferException(m, info, transfer)

class PeerBusyException(m: String, info: String, transfer: Transfer? = null) : TransferException(m, info, transfer)
