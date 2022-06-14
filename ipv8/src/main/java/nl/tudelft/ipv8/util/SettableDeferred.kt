package nl.tudelft.ipv8.util

import kotlinx.coroutines.*

class SettableDeferred<T> {

    private val job = GlobalScope.async(start = CoroutineStart.LAZY) {
        try {
            while (isActive) {
                delay(50)
            }
        } catch (e: CancellationException) {
            // NO-OP
        }
    }

    private var result: T? = null

    suspend fun await(): T? {
        // We are manually throwing an error and, thus, want to return in both cases.
        @Suppress("ReturnInsideFinallyBlock")
        try {
            job.await()
        } finally {
            return result
        }
    }

    fun setResult(result: T) {
        this.result = result
        job.cancel(CancellationException("Value has been set."))
    }
}
