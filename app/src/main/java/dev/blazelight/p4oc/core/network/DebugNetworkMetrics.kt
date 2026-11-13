package dev.blazelight.p4oc.core.network

import dev.blazelight.p4oc.core.log.AppLog
import okhttp3.Call
import okhttp3.EventListener
import okhttp3.Interceptor
import okhttp3.Response
import okhttp3.ResponseBody.Companion.asResponseBody
import okio.Buffer
import okio.ForwardingSource
import okio.buffer
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Debug-only network accounting. Installed by [ConnectionManager] only when
 * `BuildConfig.DEBUG` is true and stripped from release builds by R8.
 *
 * Request bytes are reported by OkHttp's [EventListener.requestBodyEnd]; response bytes are
 * counted at the network layer so compressed bodies are measured on the wire. WebSocket frames
 * are not counted (OkHttp exposes no HTTP body for them).
 */
internal object DebugNetworkMetrics {

    private const val TAG = "NetMetrics"
    private const val LOG_EVERY_CALLS = 20L
    private const val LIVE_LOG_BYTES = 8L * 1024 * 1024

    private data class Key(val method: String, val host: String, val path: String)

    private class Totals {
        var calls: Long = 0
        var requestBytes: Long = 0
        var responseBytes: Long = 0
        var encoding: String? = null
        var protocol: String? = null
    }

    private class CallState {
        val requestBytes = AtomicLong()
        val responseBytes = AtomicLong()
        @Volatile var encoding: String? = null
        @Volatile var protocol: String? = null
    }

    private val states = ConcurrentHashMap<Call, CallState>()
    private val endpoints = ConcurrentHashMap<Key, Totals>()
    private val liveRequestBytes = AtomicLong()
    private val liveResponseBytes = AtomicLong()
    private val completedCalls = AtomicLong()

    fun eventListener(): EventListener = object : EventListener() {
        override fun callStart(call: Call) {
            states[call] = CallState()
        }

        override fun requestBodyEnd(call: Call, byteCount: Long) {
            if (byteCount <= 0) return
            states[call]?.requestBytes?.addAndGet(byteCount)
            liveRequestBytes.addAndGet(byteCount)
        }

        override fun responseHeadersEnd(call: Call, response: Response) {
            val state = states[call] ?: return
            state.encoding = response.header("Content-Encoding")
            state.protocol = response.protocol.toString()
        }

        override fun callEnd(call: Call) {
            finish(call)
        }

        override fun callFailed(call: Call, ioe: IOException) {
            finish(call)
        }
    }

    fun networkInterceptor(): Interceptor = Interceptor { chain ->
        val call = chain.call()
        val state = states.computeIfAbsent(call) { CallState() }
        val response = chain.proceed(chain.request())
        val body = response.body
        val counting = object : ForwardingSource(body.source()) {
            override fun read(sink: Buffer, byteCount: Long): Long {
                val read = super.read(sink, byteCount)
                if (read > 0) {
                    val before = state.responseBytes.get()
                    val after = before + read
                    state.responseBytes.set(after)
                    liveResponseBytes.addAndGet(read)
                    if (after / LIVE_LOG_BYTES > before / LIVE_LOG_BYTES) {
                        AppLog.i(TAG) { summary() }
                    }
                }
                return read
            }
        }
        response.newBuilder()
            .body(counting.buffer().asResponseBody(body.contentType(), body.contentLength()))
            .build()
    }

    private fun finish(call: Call) {
        val state = states.remove(call) ?: return
        val request = call.request()
        record(
            method = request.method,
            host = request.url.host,
            path = normalizePath(request.url.encodedPath),
            requestBytes = state.requestBytes.get(),
            responseBytes = state.responseBytes.get(),
            encoding = state.encoding,
            protocol = state.protocol,
        )
        if (completedCalls.incrementAndGet() % LOG_EVERY_CALLS == 0L) {
            AppLog.i(TAG) { summary() }
        }
    }

    internal fun record(
        method: String,
        host: String,
        path: String,
        requestBytes: Long,
        responseBytes: Long,
        encoding: String?,
        protocol: String?,
    ) {
        val key = Key(method, host, path)
        endpoints.compute(key) { _, existing ->
            (existing ?: Totals()).apply {
                calls += 1
                this.requestBytes += requestBytes
                this.responseBytes += responseBytes
                if (encoding != null) this.encoding = encoding
                if (protocol != null) this.protocol = protocol
            }
        }
    }

    internal fun summary(): String {
        val builder = StringBuilder()
        builder.append("NetMetrics: req=")
            .append(liveRequestBytes.get())
            .append("B resp=")
            .append(liveResponseBytes.get())
            .append("B calls=")
            .append(completedCalls.get())
        if (endpoints.isNotEmpty()) {
            endpoints.entries
                .sortedByDescending { it.value.requestBytes + it.value.responseBytes }
                .forEach { (key, totals) ->
                    builder.append("\n  ")
                        .append(key.method)
                        .append(' ')
                        .append(key.host)
                        .append(key.path)
                        .append(" req=")
                        .append(totals.requestBytes)
                        .append("B resp=")
                        .append(totals.responseBytes)
                        .append("B calls=")
                        .append(totals.calls)
                        .append(" enc=")
                        .append(totals.encoding ?: "-")
                        .append(" proto=")
                        .append(totals.protocol ?: "-")
                }
        }
        return builder.toString()
    }

    internal fun debugTotalBytes(): Long =
        endpoints.values.sumOf { it.requestBytes + it.responseBytes }

    internal fun reset() {
        states.clear()
        endpoints.clear()
        liveRequestBytes.set(0)
        liveResponseBytes.set(0)
        completedCalls.set(0)
    }

    private fun normalizePath(path: String): String {
        if (path.isEmpty()) return "/"
        return path.split('/').joinToString("/") { segment ->
            when {
                segment.length >= 20 && segment.all { it.isLetterOrDigit() || it == '-' || it == '_' } -> "{id}"
                segment.length >= 6 && segment.all { it.isDigit() } -> "{id}"
                else -> segment
            }
        }
    }
}
