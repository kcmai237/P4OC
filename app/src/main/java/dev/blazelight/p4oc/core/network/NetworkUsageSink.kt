package dev.blazelight.p4oc.core.network

interface NetworkUsageSink {
    fun onRequestBytes(endpoint: String, bytes: Long)

    fun onResponseBytes(endpoint: String, bytes: Long)

    fun onCallCompleted(endpoint: String, encoding: String?, protocol: String?)

    object None : NetworkUsageSink {
        override fun onRequestBytes(endpoint: String, bytes: Long) = Unit

        override fun onResponseBytes(endpoint: String, bytes: Long) = Unit

        override fun onCallCompleted(endpoint: String, encoding: String?, protocol: String?) = Unit
    }
}
