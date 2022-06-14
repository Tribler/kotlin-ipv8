package nl.tudelft.ipv8.messaging.eva

/**
 * Scheduled task with an execution time and action
 *
 * @param atTime the execution time in millis
 * @param action the task
 */
internal data class ScheduledTask(
    val atTime: Long,
    val action: () -> Unit
) : Comparable<ScheduledTask> {
    override fun compareTo(other: ScheduledTask) = when {
        atTime < other.atTime -> -1
        atTime > other.atTime -> 1
        else -> 0
    }
}
