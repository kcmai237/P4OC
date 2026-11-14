package dev.blazelight.p4oc.core.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class NetworkMetricsTest {

    @Before
    fun setUp() {
        NetworkMetrics.reset()
    }

    @Test
    fun `aggregates request and response bytes per endpoint`() {
        NetworkMetrics.record(
            method = "GET",
            host = "example.test",
            path = "/api/session",
            requestBytes = 0,
            responseBytes = 1_200,
            encoding = "gzip",
            protocol = "h2",
        )
        NetworkMetrics.record(
            method = "GET",
            host = "example.test",
            path = "/api/session",
            requestBytes = 0,
            responseBytes = 800,
            encoding = "gzip",
            protocol = "h2",
        )
        NetworkMetrics.record(
            method = "POST",
            host = "example.test",
            path = "/api/session/{id}/message",
            requestBytes = 64,
            responseBytes = 900,
            encoding = null,
            protocol = "http/1.1",
        )

        val summary = NetworkMetrics.summary()
        println(summary)

        assertTrue(summary.contains("GET example.test/api/session req=0B resp=2000B calls=2"))
        assertTrue(summary.contains("POST example.test/api/session/{id}/message req=64B resp=900B"))
        assertTrue(summary.contains("enc=gzip"))
        assertTrue(summary.contains("proto=h2"))
        assertEquals(2_964L, NetworkMetrics.debugTotalBytes())
    }

    @Test
    fun `summary is empty of endpoints after reset`() {
        NetworkMetrics.record("GET", "example.test", "/x", 1, 2, null, "h2")
        NetworkMetrics.reset()
        assertTrue(NetworkMetrics.summary().contains("calls=0"))
        assertTrue(!NetworkMetrics.summary().contains("example.test"))
    }
}
