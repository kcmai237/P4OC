package dev.blazelight.p4oc.core.network

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class RequestCoalescerTest {

    @Test
    fun `concurrent identical loads share one request`() = runTest {
        val coalescer = RequestCoalescer<String, String>(ttlMillis = 30_000L)
        val calls = AtomicInteger()
        val gate = CompletableDeferred<Unit>()
        val load: suspend () -> String = {
            calls.incrementAndGet()
            gate.await()
            "catalog"
        }

        val first = async { coalescer.get("key", load) }
        runCurrent()
        val second = async { coalescer.get("key", load) }
        runCurrent()
        gate.complete(Unit)

        assertEquals("catalog", first.await())
        assertEquals("catalog", second.await())
        assertEquals(1, calls.get())
    }

    @Test
    fun `a successful value is reused only inside the TTL window`() = runTest {
        var now = 1_000L
        val coalescer = RequestCoalescer<String, Int>(ttlMillis = 30_000L, clock = { now })
        val calls = AtomicInteger()
        val load: suspend () -> Int = { calls.incrementAndGet(); 7 }

        assertEquals(7, coalescer.get("key", load))
        assertEquals(7, coalescer.get("key", load))
        assertEquals(1, calls.get())

        now += 30_001L
        assertEquals(7, coalescer.get("key", load))
        assertEquals(2, calls.get())
    }

    @Test
    fun `failures reach every caller and are not retained`() = runTest {
        val coalescer = RequestCoalescer<String, Int>(ttlMillis = 30_000L)
        val calls = AtomicInteger()
        val gate = CompletableDeferred<Unit>()
        val failing: suspend () -> Int = {
            calls.incrementAndGet()
            gate.await()
            throw IllegalStateException("boom")
        }

        val first = async { runCatching { coalescer.get("key", failing) } }
        runCurrent()
        val second = async { runCatching { coalescer.get("key", failing) } }
        runCurrent()
        gate.complete(Unit)

        assertTrue(first.await().isFailure)
        assertTrue(second.await().isFailure)
        assertEquals(1, calls.get())

        val recovered = coalescer.get("key") { calls.incrementAndGet(); 9 }
        assertEquals(9, recovered)
        assertEquals(2, calls.get())
    }

    @Test
    fun `cache is bounded by the configured entry count`() = runTest {
        var now = 0L
        val coalescer = RequestCoalescer<String, Int>(
            ttlMillis = 30_000L,
            maxEntries = 2,
            clock = { now },
        )
        val calls = AtomicInteger()
        val load: suspend (String) -> Int = { key -> calls.incrementAndGet(); key.length }

        coalescer.get("alpha", { load("alpha") })
        coalescer.get("beta", { load("beta") })
        coalescer.get("gamma", { load("gamma") })
        now += 1L
        coalescer.get("alpha", { load("alpha") })

        assertEquals(4, calls.get())
    }
}
