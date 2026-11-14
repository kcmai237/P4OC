package dev.blazelight.p4oc.core.network

data class EndpointUsage(
    val label: String,
    val requestBytes: Long,
    val responseBytes: Long,
    val calls: Long,
)

data class DataUsageSnapshot(
    val date: String,
    val requestBytes: Long,
    val responseBytes: Long,
    val calls: Long,
    val encodings: Map<String, Long>,
    val endpoints: List<EndpointUsage>,
) {
    val totalBytes: Long get() = requestBytes + responseBytes
}

class DataUsageAccumulator(
    private val today: () -> String,
    private val maxTrackedEndpoints: Int = 200,
) {
    private class EndpointStats(
        var requestBytes: Long = 0,
        var responseBytes: Long = 0,
        var calls: Long = 0,
    )

    private val lock = Any()
    private var date = today()
    private var requestBytes = 0L
    private var responseBytes = 0L
    private var calls = 0L
    private val encodings = mutableMapOf<String, Long>()
    private val endpoints = LinkedHashMap<String, EndpointStats>()

    fun restore(storedDate: String, storedRequestBytes: Long, storedResponseBytes: Long, storedCalls: Long) {
        synchronized(lock) {
            if (storedDate == today()) {
                date = storedDate
                requestBytes = storedRequestBytes
                responseBytes = storedResponseBytes
                calls = storedCalls
            }
        }
    }

    fun addRequestBytes(endpoint: String, bytes: Long) {
        if (bytes <= 0) return
        synchronized(lock) {
            rolloverLocked()
            requestBytes += bytes
            endpointOf(endpoint).requestBytes += bytes
        }
    }

    fun addResponseBytes(endpoint: String, bytes: Long) {
        if (bytes <= 0) return
        synchronized(lock) {
            rolloverLocked()
            responseBytes += bytes
            endpointOf(endpoint).responseBytes += bytes
        }
    }

    fun addCall(endpoint: String, encoding: String?) {
        synchronized(lock) {
            rolloverLocked()
            calls += 1
            endpointOf(endpoint).calls += 1
            val key = encoding?.takeIf { it.isNotBlank() } ?: IDENTITY
            encodings[key] = (encodings[key] ?: 0) + 1
        }
    }

    fun snapshot(): DataUsageSnapshot = synchronized(lock) {
        rolloverLocked()
        DataUsageSnapshot(
            date = date,
            requestBytes = requestBytes,
            responseBytes = responseBytes,
            calls = calls,
            encodings = encodings.toMap(),
            endpoints = endpoints.entries
                .sortedByDescending { it.value.requestBytes + it.value.responseBytes }
                .take(TOP_ENDPOINTS)
                .map { (label, stats) ->
                    EndpointUsage(label, stats.requestBytes, stats.responseBytes, stats.calls)
                },
        )
    }

    fun reset() {
        synchronized(lock) {
            date = today()
            requestBytes = 0
            responseBytes = 0
            calls = 0
            encodings.clear()
            endpoints.clear()
        }
    }

    private fun rolloverLocked() {
        val current = today()
        if (current == date) return
        date = current
        requestBytes = 0
        responseBytes = 0
        calls = 0
        encodings.clear()
        endpoints.clear()
    }

    private fun endpointOf(label: String): EndpointStats {
        endpoints[label]?.let { return it }
        if (endpoints.size >= maxTrackedEndpoints) {
            endpoints.entries.minByOrNull { it.value.requestBytes + it.value.responseBytes }
                ?.let { endpoints.remove(it.key) }
        }
        return EndpointStats().also { endpoints[label] = it }
    }

    companion object {
        const val TOP_ENDPOINTS = 8
        private const val IDENTITY = "identity"
    }
}
