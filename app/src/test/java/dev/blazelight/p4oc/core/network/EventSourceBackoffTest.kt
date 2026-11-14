package dev.blazelight.p4oc.core.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random

class EventSourceBackoffTest {

    @Test
    fun `delay grows exponentially and is capped`() {
        val backoff = ReconnectBackoff(random = { 0.5 })
        val delays = (1..8).map { backoff.nextDelayMillis() }

        assertEquals(
            listOf(1_000L, 2_000L, 4_000L, 8_000L, 16_000L, 30_000L, 30_000L, 30_000L),
            delays,
        )
        assertTrue(delays.all { it <= ReconnectBackoff.MAX_DELAY_MILLIS })
    }

    @Test
    fun `jitter keeps draws within bounds and non-degenerate over many samples`() {
        val random = Random(7)
        val backoff = ReconnectBackoff(random = random::nextDouble)
        val draws = (1..200).map { backoff.nextDelayMillis() }

        assertTrue(draws.all { it in 0..ReconnectBackoff.MAX_DELAY_MILLIS })
        assertTrue("expected varied jitter output", draws.distinct().size > 5)
        assertTrue("expected a non-trivial spread", (draws.max() - draws.min()) > 1_000L)
    }

    @Test
    fun `reset returns to the base delay and clears attempts`() {
        val backoff = ReconnectBackoff(random = { 0.5 })
        repeat(10) { backoff.nextDelayMillis() }
        assertEquals(10, backoff.attempts)

        backoff.reset()

        assertEquals(0, backoff.attempts)
        assertEquals(ReconnectBackoff.BASE_DELAY_MILLIS, backoff.nextDelayMillis())
    }

    @Test
    fun `injected bounds are honoured`() {
        val backoff = ReconnectBackoff(
            baseDelayMillis = 100,
            maxDelayMillis = 500,
            jitterRatio = 0.0,
            random = { 0.5 },
        )

        assertEquals(listOf(100L, 200L, 400L, 500L, 500L), (1..5).map { backoff.nextDelayMillis() })
    }
}
