package dev.blazelight.p4oc.core.network

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

interface NetworkMeteredSource {
    val metered: Flow<Boolean>

    object Unmetered : NetworkMeteredSource {
        override val metered: Flow<Boolean> = flowOf(false)
    }
}

/**
 * Data saver. It is ON by default on every network and only gates non-essential REST refreshes:
 * SSE, message delivery, terminal output and OFISH transfers never consult it.
 */
interface DataSaverPolicy {
    val active: StateFlow<Boolean>

    object Disabled : DataSaverPolicy {
        private val state = MutableStateFlow(false)
        override val active: StateFlow<Boolean> = state.asStateFlow()
    }
}

class MutableDataSaverPolicy(
    enabled: Flow<Boolean>,
    scope: CoroutineScope,
) : DataSaverPolicy {
    override val active: StateFlow<Boolean> =
        enabled.map { isDataSaverActive(it) }
            .distinctUntilChanged()
            .stateIn(scope, SharingStarted.Eagerly, true)
}

internal fun isDataSaverActive(optIn: Boolean): Boolean = optIn
