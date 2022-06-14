package nl.tudelft.ipv8.messaging.eva

import mu.KotlinLogging
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.*

class ScheduledTaskTest {
    private val logger = KotlinLogging.logger {}

    @Test
    fun scheduledTask_init() {
        val atTime = Date().time + 1000
        val action = {
            logger.debug { "init" }
        }
        val scheduledTask = ScheduledTask(atTime, action)

        assertEquals(atTime, scheduledTask.atTime)
        assertEquals(action, scheduledTask.action)
    }

    @Test
    fun scheduledTask_compare() {
        val action = {
            logger.debug { "compare one" }
        }
        val atTimes = listOf(5000L, 10000L, 1000L)

        val priorityQueue = PriorityQueue<ScheduledTask>()
        priorityQueue.add(ScheduledTask(atTimes[0], action))
        priorityQueue.add(ScheduledTask(atTimes[1], action))

        assertEquals(atTimes[0], priorityQueue.peek().atTime)

        priorityQueue.add(ScheduledTask(atTimes[2], action))

        assertEquals(atTimes[2], priorityQueue.peek().atTime)

        priorityQueue.add(ScheduledTask(atTimes[2], action))

        assertEquals(atTimes[2], priorityQueue.peek().atTime)
    }
}
