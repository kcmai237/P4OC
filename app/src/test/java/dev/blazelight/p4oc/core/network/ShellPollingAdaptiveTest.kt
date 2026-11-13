package dev.blazelight.p4oc.core.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ShellPollingAdaptiveTest {

    @Test
    fun `interval stays within the floor and the ceiling`() {
        var now = 0L
        val polling = AdaptiveShellPolling(clock = { now })

        val draws = (1..120).map {
            now += 100L
            polling.nextIntervalMillis()
        }

        assertTrue(draws.all { it in 250L..1_000L })
        assertEquals(250L, draws.first())
        assertEquals(1_000L, draws.last())
    }

    @Test
    fun `interval is monotone and reaches the ceiling without oscillating`() {
        var now = 0L
        val polling = AdaptiveShellPolling(clock = { now })

        var previous = 0L
        repeat(100) {
            now += 100L
            val next = polling.nextIntervalMillis()
            assertTrue("interval must never decrease", next >= previous)
            previous = next
        }

        assertEquals(1_000L, previous)
    }

    @Test
    fun `a non-monotonic clock cannot make the interval oscillate`() {
        var now = 0L
        val polling = AdaptiveShellPolling(clock = { now })

        now += 20_000L
        assertEquals(1_000L, polling.nextIntervalMillis())

        now -= 15_000L
        assertEquals("interval must not shrink when the clock moves backwards", 1_000L, polling.nextIntervalMillis())
    }

    @Test
    fun `injected bounds are honoured`() {
        var now = 0L
        val polling = AdaptiveShellPolling(
            minIntervalMillis = 100L,
            maxIntervalMillis = 300L,
            clock = { now },
        )

        assertEquals(100L, polling.nextIntervalMillis())
        now += AdaptiveShellPolling.GROWTH_WINDOW_MILLIS
        assertEquals(200L, polling.nextIntervalMillis())
        now += AdaptiveShellPolling.GROWTH_WINDOW_MILLIS
        assertEquals(300L, polling.nextIntervalMillis())
        now += AdaptiveShellPolling.GROWTH_WINDOW_MILLIS
        assertEquals(300L, polling.nextIntervalMillis())
    }
}
