package dev.blazelight.p4oc.core.network

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn

/** Source of the current network's metered flag (Android `NetworkCapabilities`). */
interface NetworkMeteredSource {
    val metered: Flow<Boolean>

    /** No-op source for tests and non-Android callers: always treated as unmetered (base path). */
    object Unmetered : NetworkMeteredSource {
        override val metered: Flow<Boolean> = flowOf(false)
    }
}

/**
 * O11 user-opt-in data saver. [active] is true only when the user enabled the mode **and** the
 * current network is metered, so Wi-Fi behavior is unchanged. The mode only gates non-essential
 * REST refreshes: SSE, message delivery, terminal output and OFISH transfers never consult it.
 */
interface DataSaverPolicy {
    val active: StateFlow<Boolean>

    /** Default OFF: identical to the base behavior. */
    object Disabled : DataSaverPolicy {
        private val state = MutableStateFlow(false)
        override val active: StateFlow<Boolean> = state.asStateFlow()
    }
}

class MutableDataSaverPolicy(
    enabled: Flow<Boolean>,
    metered: Flow<Boolean>,
    scope: CoroutineScope,
) : DataSaverPolicy {
    override val active: StateFlow<Boolean> =
        combine(enabled, metered) { optIn, isMetered -> isDataSaverActive(optIn, isMetered) }
            .distinctUntilChanged()
            .stateIn(scope, SharingStarted.Eagerly, false)
}

/** The data saver only engages when the user opted in **and** the network is metered. */
internal fun isDataSaverActive(optIn: Boolean, metered: Boolean): Boolean = optIn && metered
