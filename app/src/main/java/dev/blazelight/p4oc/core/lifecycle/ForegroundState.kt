package dev.blazelight.p4oc.core.lifecycle

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Process-level foreground state. Drives O10 partial pause: non-SSE background work is
 * suspended while the app is not in the foreground and resumed exactly once on return.
 * SSE itself never consults this state (veto F31).
 */
interface ForegroundState {
    val isForeground: StateFlow<Boolean>

    /** Updated by the app lifecycle effect; a no-op for the always-foreground state. */
    fun setForeground(foreground: Boolean) = Unit

    /** No-op state for tests and non-lifecycle callers; always foreground. */
    object AlwaysForeground : ForegroundState {
        private val state = MutableStateFlow(true)
        override val isForeground: StateFlow<Boolean> = state.asStateFlow()
    }
}

/** Lifecycle-driven state, updated by the app lifecycle effect. */
class MutableForegroundState(initialForeground: Boolean = true) : ForegroundState {
    private val state = MutableStateFlow(initialForeground)
    override val isForeground: StateFlow<Boolean> = state.asStateFlow()

    override fun setForeground(foreground: Boolean) {
        state.value = foreground
    }
}
