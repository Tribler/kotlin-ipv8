package nl.tudelft.ipv8.messaging.eva

import org.junit.Assert.assertEquals
import org.junit.Test

class TransferExceptionTest {
    private val message = "Message"
    private val info = "Info"

    @Test
    fun sizeException() {
        val scheduledTransfer = ScheduledTransfer(info, byteArrayOf(), 1.toULong(), "012345678", 10, 100.toULong(), 10, 80)
        val transfer = Transfer(TransferType.INCOMING, scheduledTransfer)
        val exception = SizeException(message, info, transfer)

        assertEquals(message, exception.m)
        assertEquals(info, exception.info)
        assertEquals(TransferType.INCOMING, exception.transfer?.type)
    }

    @Test
    fun timeoutException() {
        val scheduledTransfer = ScheduledTransfer(info, byteArrayOf(), 1.toULong(), "012345678", 10, 100.toULong(), 10, 80)
        val transfer = Transfer(TransferType.INCOMING, scheduledTransfer)
        val exception = TimeoutException(message, info, transfer)

        assertEquals(message, exception.m)
        assertEquals(info, exception.info)
        assertEquals(TransferType.INCOMING, exception.transfer?.type)
    }

    @Test
    fun peerBusyException() {
        val scheduledTransfer = ScheduledTransfer(info, byteArrayOf(), 1.toULong(), "012345678", 10, 100.toULong(), 10, 80)
        val transfer = Transfer(TransferType.INCOMING, scheduledTransfer)
        val exception = PeerBusyException(message, info, transfer)

        assertEquals(message, exception.m)
        assertEquals(info, exception.info)
        assertEquals(TransferType.INCOMING, exception.transfer?.type)
    }
}
