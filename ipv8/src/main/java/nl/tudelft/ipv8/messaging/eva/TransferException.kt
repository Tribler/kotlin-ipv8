package nl.tudelft.ipv8.messaging.eva

class TransferException(
    val m: String,
    val exception: Exception
)

class ValueException(m: String, exception: Exception) : TransferException(m, exception)

class TimeoutException(m: Sstring, exception: Exception) : TransferException(m, exception)

class
