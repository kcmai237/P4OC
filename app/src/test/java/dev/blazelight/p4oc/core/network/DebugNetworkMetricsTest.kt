package dev.blazelight.p4oc.core.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class DebugNetworkMetricsTest {

    @Before
    fun setUp() {
        DebugNetworkMetrics.reset()
    }

    @Test
    fun `aggregates request and response bytes per endpoint`() {
        DebugNetworkMetrics.record(
            method = "GET",
            host = "example.test",
            path = "/api/session",
            requestBytes = 0,
            responseBytes = 1_200,
            encoding = "gzip",
            protocol = "h2",
        )
        DebugNetworkMetrics.record(
            method = "GET",
            host = "example.test",
            path = "/api/session",
            requestBytes = 0,
            responseBytes = 800,
            encoding = "gzip",
            protocol = "h2",
        )
        DebugNetworkMetrics.record(
            method = "POST",
            host = "example.test",
            path = "/api/session/{id}/message",
            requestBytes = 64,
            responseBytes = 900,
            encoding = null,
            protocol = "http/1.1",
        )

        val summary = DebugNetworkMetrics.summary()
        println(summary)

        assertTrue(summary.contains("GET example.test/api/session req=0B resp=2000B calls=2"))
        assertTrue(summary.contains("POST example.test/api/session/{id}/message req=64B resp=900B"))
        assertTrue(summary.contains("enc=gzip"))
        assertTrue(summary.contains("proto=h2"))
        assertEquals(2_964L, DebugNetworkMetrics.debugTotalBytes())
    }

    @Test
    fun `summary is empty of endpoints after reset`() {
        DebugNetworkMetrics.record("GET", "example.test", "/x", 1, 2, null, "h2")
        DebugNetworkMetrics.reset()
        assertTrue(DebugNetworkMetrics.summary().contains("calls=0"))
        assertTrue(!DebugNetworkMetrics.summary().contains("example.test"))
    }
}
