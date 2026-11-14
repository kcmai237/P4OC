package dev.blazelight.p4oc.core.network

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

interface DataUsageSource {
    val snapshot: StateFlow<DataUsageSnapshot>

    fun reset()

    object None : DataUsageSource {
        private val state = MutableStateFlow(
            DataUsageSnapshot(
                date = "",
                requestBytes = 0,
                responseBytes = 0,
                calls = 0,
                encodings = emptyMap(),
                endpoints = emptyList(),
            ),
        )
        override val snapshot: StateFlow<DataUsageSnapshot> = state.asStateFlow()

        override fun reset() = Unit
    }
}
