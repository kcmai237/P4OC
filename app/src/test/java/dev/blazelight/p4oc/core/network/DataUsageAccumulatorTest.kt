package dev.blazelight.p4oc.core.network

import org.junit.Assert.assertEquals
import org.junit.Test

class DataUsageAccumulatorTest {

    @Test
    fun `aggregates bytes and calls per endpoint`() {
        val accumulator = DataUsageAccumulator(today = { "2026-11-14" })

        accumulator.addRequestBytes("GET example.test/a", 40)
        accumulator.addResponseBytes("GET example.test/a", 1_000)
        accumulator.addCall("GET example.test/a", encoding = "gzip")
        accumulator.addResponseBytes("POST example.test/b", 500)
        accumulator.addCall("POST example.test/b", encoding = null)

        val snapshot = accumulator.snapshot()
        assertEquals(40L, snapshot.requestBytes)
        assertEquals(1_500L, snapshot.responseBytes)
        assertEquals(1_540L, snapshot.totalBytes)
        assertEquals(2L, snapshot.calls)
        assertEquals("2026-11-14", snapshot.date)
        assertEquals("GET example.test/a", snapshot.endpoints.first().label)
        assertEquals(1_040L, snapshot.endpoints.first().requestBytes + snapshot.endpoints.first().responseBytes)
        assertEquals(1L, snapshot.encodings["gzip"])
        assertEquals(1L, snapshot.encodings["identity"])
    }

    @Test
    fun `rolls over when the day changes`() {
        var date = "2026-11-14"
        val accumulator = DataUsageAccumulator(today = { date })
        accumulator.addResponseBytes("GET example.test/a", 1_000)
        accumulator.addCall("GET example.test/a", encoding = "gzip")

        date = "2026-11-15"

        val snapshot = accumulator.snapshot()
        assertEquals("2026-11-15", snapshot.date)
        assertEquals(0L, snapshot.totalBytes)
        assertEquals(0L, snapshot.calls)
        assertEquals(emptyMap<String, Long>(), snapshot.encodings)
    }

    @Test
    fun `restores totals only from the same day`() {
        val accumulator = DataUsageAccumulator(today = { "2026-11-14" })
        accumulator.restore("2026-11-13", 10, 20, 1)
        assertEquals(0L, accumulator.snapshot().totalBytes)

        accumulator.restore("2026-11-14", 30, 40, 2)
        assertEquals(70L, accumulator.snapshot().totalBytes)
        assertEquals(2L, accumulator.snapshot().calls)
    }

    @Test
    fun `reset clears the current day`() {
        val accumulator = DataUsageAccumulator(today = { "2026-11-14" })
        accumulator.addResponseBytes("GET example.test/a", 1_000)
        accumulator.reset()
        assertEquals(0L, accumulator.snapshot().totalBytes)
        assertEquals(emptyList<EndpointUsage>(), accumulator.snapshot().endpoints)
    }
}
