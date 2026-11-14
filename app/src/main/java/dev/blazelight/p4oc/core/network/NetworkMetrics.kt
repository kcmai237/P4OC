package dev.blazelight.p4oc.core.network

import dev.blazelight.p4oc.BuildConfig
import dev.blazelight.p4oc.core.log.AppLog
import okhttp3.Call
import okhttp3.EventListener
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.asResponseBody
import okio.Buffer
import okio.ForwardingSource
import okio.buffer
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Always-on network accounting. Response bytes are counted at the network layer so compressed
 * bodies are measured on the wire; SSE is counted as it streams. WebSocket frames are not counted
 * by OkHttp and are therefore absent from the totals.
 */
internal object NetworkMetrics {

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
        @Volatile var endpoint: String = ""
    }

    private val states = ConcurrentHashMap<Call, CallState>()
    private val endpoints = ConcurrentHashMap<Key, Totals>()
    private val liveRequestBytes = AtomicLong()
    private val liveResponseBytes = AtomicLong()
    private val completedCalls = AtomicLong()

    fun eventListener(sink: NetworkUsageSink = NetworkUsageSink.None): EventListener = object : EventListener() {
        override fun callStart(call: Call) {
            states[call] = CallState().also { it.endpoint = endpointLabel(call.request()) }
        }

        override fun requestBodyEnd(call: Call, byteCount: Long) {
            if (byteCount <= 0) return
            val state = states[call] ?: return
            state.requestBytes.addAndGet(byteCount)
            liveRequestBytes.addAndGet(byteCount)
            sink.onRequestBytes(state.endpoint, byteCount)
        }

        override fun responseHeadersEnd(call: Call, response: Response) {
            val state = states[call] ?: return
            state.encoding = response.header("Content-Encoding")
            state.protocol = response.protocol.toString()
        }

        override fun callEnd(call: Call) {
            finish(call, sink)
        }

        override fun callFailed(call: Call, ioe: IOException) {
            finish(call, sink)
        }
    }

    fun networkInterceptor(sink: NetworkUsageSink = NetworkUsageSink.None): Interceptor = Interceptor { chain ->
        val call = chain.call()
        val request = chain.request()
        val endpoint = endpointLabel(request)
        val response = chain.proceed(request)
        val body = response.body
        val counting = object : ForwardingSource(body.source()) {
            override fun read(sinkBuffer: Buffer, byteCount: Long): Long {
                val read = super.read(sinkBuffer, byteCount)
                if (read > 0) {
                    states[call]?.responseBytes?.addAndGet(read)
                    val before = liveResponseBytes.get()
                    val after = before + read
                    liveResponseBytes.set(after)
                    sink.onResponseBytes(endpoint, read)
                    if (BuildConfig.DEBUG && after / LIVE_LOG_BYTES > before / LIVE_LOG_BYTES) {
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

    private fun finish(call: Call, sink: NetworkUsageSink) {
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
        sink.onCallCompleted(state.endpoint, state.encoding, state.protocol)
        val completed = completedCalls.incrementAndGet()
        if (BuildConfig.DEBUG && completed % LOG_EVERY_CALLS == 0L) {
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

    private fun endpointLabel(request: Request): String =
        "${request.method} ${request.url.host}${normalizePath(request.url.encodedPath)}"

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
