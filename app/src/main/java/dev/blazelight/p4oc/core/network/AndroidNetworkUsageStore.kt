package dev.blazelight.p4oc.core.network

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.concurrent.atomic.AtomicBoolean

class AndroidNetworkUsageStore(
    context: Context,
    private val today: () -> String = { LocalDate.now().toString() },
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    private val flushIntervalMs: Long = 2_000L,
) : NetworkUsageSink, DataUsageSource {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val accumulator = DataUsageAccumulator(today)
    private val _snapshot = MutableStateFlow(accumulator.snapshot())
    override val snapshot: StateFlow<DataUsageSnapshot> = _snapshot.asStateFlow()
    private val dirty = AtomicBoolean(false)

    init {
        accumulator.restore(
            prefs.getString(KEY_DATE, null) ?: "",
            prefs.getLong(KEY_REQUEST_BYTES, 0L),
            prefs.getLong(KEY_RESPONSE_BYTES, 0L),
            prefs.getLong(KEY_CALLS, 0L),
        )
        _snapshot.value = accumulator.snapshot()
        scope.launch {
            while (isActive) {
                delay(flushIntervalMs)
                flush()
            }
        }
    }

    override fun onRequestBytes(endpoint: String, bytes: Long) {
        accumulator.addRequestBytes(endpoint, bytes)
        dirty.set(true)
    }

    override fun onResponseBytes(endpoint: String, bytes: Long) {
        accumulator.addResponseBytes(endpoint, bytes)
        dirty.set(true)
    }

    override fun onCallCompleted(endpoint: String, encoding: String?, protocol: String?) {
        accumulator.addCall(endpoint, encoding)
        dirty.set(true)
    }

    fun flush() {
        val snapshot = accumulator.snapshot()
        if (dirty.getAndSet(false)) {
            prefs.edit()
                .putString(KEY_DATE, snapshot.date)
                .putLong(KEY_REQUEST_BYTES, snapshot.requestBytes)
                .putLong(KEY_RESPONSE_BYTES, snapshot.responseBytes)
                .putLong(KEY_CALLS, snapshot.calls)
                .apply()
        }
        if (_snapshot.value != snapshot) {
            _snapshot.value = snapshot
        }
    }

    override fun reset() {
        accumulator.reset()
        dirty.set(true)
        flush()
    }

    private companion object {
        const val PREFS = "network_usage"
        const val KEY_DATE = "date"
        const val KEY_REQUEST_BYTES = "request_bytes"
        const val KEY_RESPONSE_BYTES = "response_bytes"
        const val KEY_CALLS = "calls"
    }
}
