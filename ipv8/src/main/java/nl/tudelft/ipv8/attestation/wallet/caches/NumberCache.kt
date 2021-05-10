package nl.tudelft.ipv8.attestation.wallet.caches

import kotlinx.coroutines.*
import mu.KotlinLogging
import nl.tudelft.ipv8.attestation.common.RequestCache
import java.math.BigInteger

private val logger = KotlinLogging.logger {}

const val DEFAULT_TIMEOUT = 180
const val SECOND_IN_MILLISECONDS = 1000L

abstract class NumberCache(
    val requestCache: RequestCache,
    val prefix: String,
    val number: BigInteger,
    open val timeout: Int = DEFAULT_TIMEOUT,
    private val onTimeout: () -> Unit = {},
) {
    private lateinit var timeOutJob: Job

    init {
        if (requestCache.has(prefix, number)) {
            throw RuntimeException("Number $number is already in use.")
        }
    }

    suspend fun start(overWrittenTimeout: Int? = null, calleeCallback: (() -> Unit)? = null) {
        try {
            val timeoutValue = (overWrittenTimeout ?: timeout) * SECOND_IN_MILLISECONDS
            withTimeout(timeoutValue) {
                timeOutJob = launch {
                    // Add some delta to ensure the timeout is triggered.
                    delay(timeoutValue + SECOND_IN_MILLISECONDS)
                }
            }
        } catch (e: TimeoutCancellationException) {
            logger.warn("Cache $prefix$number timed out")
            calleeCallback?.invoke()
            onTimeout()
        }
    }

    fun stop() {
        if (this::timeOutJob.isInitialized) {
            this.timeOutJob.cancel()
        }
    }

}
