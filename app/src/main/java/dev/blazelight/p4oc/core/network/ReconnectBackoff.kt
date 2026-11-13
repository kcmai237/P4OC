package dev.blazelight.p4oc.core.network

import kotlin.math.roundToLong

/**
 * Exponential backoff with full-range jitter for manual SSE reconnections.
 *
 * The library already backoffs its own in-stream retries; this counter spaces the
 * app-driven `reconnect()` restarts (foreground resume, oversized-event recovery)
 * that would otherwise recreate the event source and restart at the base delay.
 * Delay bounds and RNG are injectable so tests are deterministic.
 */
internal class ReconnectBackoff(
    private val baseDelayMillis: Long = BASE_DELAY_MILLIS,
    private val maxDelayMillis: Long = MAX_DELAY_MILLIS,
    private val jitterRatio: Double = JITTER_RATIO,
    private val random: () -> Double = Math::random,
) {
    init {
        require(baseDelayMillis > 0) { "baseDelayMillis must be positive" }
        require(maxDelayMillis >= baseDelayMillis) { "maxDelayMillis must be >= baseDelayMillis" }
        require(jitterRatio in 0.0..1.0) { "jitterRatio must be within [0, 1]" }
    }

    private var attempt = 0

    val attempts: Int get() = attempt

    fun nextDelayMillis(): Long {
        val exponent = attempt.coerceAtMost(MAX_EXPONENT)
        val raw = (baseDelayMillis.toDouble() * (1L shl exponent)).toLong().coerceAtMost(maxDelayMillis)
        attempt++
        val jitter = ((random() * 2.0 - 1.0) * jitterRatio * raw).roundToLong()
        return (raw + jitter).coerceIn(0L, maxDelayMillis)
    }

    fun reset() {
        attempt = 0
    }

    internal companion object {
        const val BASE_DELAY_MILLIS = 1_000L
        const val MAX_DELAY_MILLIS = 30_000L
        const val JITTER_RATIO = 0.2
        private const val MAX_EXPONENT = 20
    }
}
