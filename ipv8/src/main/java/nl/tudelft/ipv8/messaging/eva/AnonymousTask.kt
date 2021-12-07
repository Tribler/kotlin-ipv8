package nl.tudelft.ipv8.messaging.eva

data class ScheduledTask(
    val atTime: Long,
    val action: () -> Unit
) {
    override fun compare
}
