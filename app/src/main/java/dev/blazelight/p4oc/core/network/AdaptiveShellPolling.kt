package dev.blazelight.p4oc.core.network

/**
 * Bounded, monotone polling cadence for the v2 shell transcript.
 *
 * The interval starts at the historical 250 ms floor and doubles every
 * [GROWTH_WINDOW_MILLIS] of command runtime up to a 1 s ceiling. It never decreases,
 * so a long-running command is polled progressively more slowly without oscillation.
 * The clock is injectable for deterministic tests.
 */
internal class AdaptiveShellPolling(
    private val minIntervalMillis: Long = MIN_INTERVAL_MILLIS,
    private val maxIntervalMillis: Long = MAX_INTERVAL_MILLIS,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    init {
        require(minIntervalMillis > 0) { "minIntervalMillis must be positive" }
        require(maxIntervalMillis >= minIntervalMillis) { "maxIntervalMillis must be >= minIntervalMillis" }
    }

    private val startedAtMillis = clock()
    private var lastIntervalMillis = minIntervalMillis

    fun nextIntervalMillis(): Long {
        val elapsed = (clock() - startedAtMillis).coerceAtLeast(0L)
        val doublings = (elapsed / GROWTH_WINDOW_MILLIS).coerceAtMost(MAX_DOUBLINGS)
        val raw = minIntervalMillis shl doublings.toInt()
        val bounded = raw.coerceIn(minIntervalMillis, maxIntervalMillis)
        lastIntervalMillis = maxOf(lastIntervalMillis, bounded)
        return lastIntervalMillis
    }

    internal companion object {
        const val MIN_INTERVAL_MILLIS = 250L
        const val MAX_INTERVAL_MILLIS = 1_000L
        const val GROWTH_WINDOW_MILLIS = 4_000L
        private const val MAX_DOUBLINGS = 10L
    }
}
