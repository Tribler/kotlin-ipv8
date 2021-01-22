package nl.tudelft.ipv8.caches

import kotlinx.coroutines.Deferred
import java.util.concurrent.CompletableFuture
import java.util.concurrent.FutureTask

open class NumberCache(val requestCache: RequestCache, val prefix: ByteArray, val number: Int) {

    val managedFutures = arrayListOf<Pair<Deferred<Object>, Object>>()
    val timeoutDelay = 10.0

    fun registerFuture(future: Deferred<Object>, onTimeout: Object) {
        this.managedFutures.add(Pair(future, onTimeout))
    }

    fun onTimeout() {
        return
    }

    override fun toString(): String {
        return "<NumberCache $prefix-$number>"
    }


}
