package dev.blazelight.p4oc.core.network

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

internal const val CATALOG_CACHE_TTL_MILLIS: Long = 30_000L

internal data class CatalogLocation(val directory: String?, val workspace: String?)

/**
 * Shares one in-flight load between concurrent callers for the same key and, when
 * [ttlMillis] is positive, keeps the successful value for that short window. Failures
 * are never retained. The cache is bounded by [maxEntries] with LRU eviction.
 */
internal class RequestCoalescer<K : Any, V : Any>(
    private val ttlMillis: Long,
    private val maxEntries: Int = DEFAULT_MAX_ENTRIES,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private data class Entry<V>(val value: V, val storedAtMillis: Long)

    private val mutex = Mutex()
    private val entries = object : LinkedHashMap<K, Entry<V>>(maxEntries, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<K, Entry<V>>): Boolean =
            size > maxEntries
    }
    private val inFlight = mutableMapOf<K, CompletableDeferred<V>>()

    suspend fun get(key: K, load: suspend () -> V): V {
        var cached: V? = null
        var pending: CompletableDeferred<V>? = null
        var leader: CompletableDeferred<V>? = null
        mutex.withLock {
            val entry = entries[key]
            if (entry != null && isFresh(entry)) {
                cached = entry.value
            } else {
                pending = inFlight[key]
                if (pending == null) {
                    val created = CompletableDeferred<V>()
                    inFlight[key] = created
                    leader = created
                }
            }
        }
        cached?.let { return it }
        pending?.let { return it.await() }

        val deferred = requireNotNull(leader)
        return try {
            val value = load()
            withContext(NonCancellable) {
                mutex.withLock {
                    if (ttlMillis > 0L) {
                        entries[key] = Entry(value, clock())
                    }
                    inFlight.remove(key)
                }
            }
            deferred.complete(value)
            value
        } catch (error: Throwable) {
            withContext(NonCancellable) {
                mutex.withLock { inFlight.remove(key) }
            }
            deferred.completeExceptionally(error)
            throw error
        }
    }

    private fun isFresh(entry: Entry<V>): Boolean =
        ttlMillis > 0L && clock() - entry.storedAtMillis <= ttlMillis

    private companion object {
        const val DEFAULT_MAX_ENTRIES = 16
    }
}
