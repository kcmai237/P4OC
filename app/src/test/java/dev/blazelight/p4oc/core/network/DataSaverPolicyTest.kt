package dev.blazelight.p4oc.core.network

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DataSaverPolicyTest {

    @Test
    fun `inert policy stays off`() {
        assertFalse(DataSaverPolicy.Disabled.active.value)
    }

    @Test
    fun `data saver engages on every network when opted in`() {
        assertFalse(isDataSaverActive(optIn = false))
        assertTrue(isDataSaverActive(optIn = true))
    }
}
