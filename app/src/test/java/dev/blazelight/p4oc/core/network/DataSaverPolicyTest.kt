package dev.blazelight.p4oc.core.network

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DataSaverPolicyTest {

    @Test
    fun `data saver is off by default`() {
        assertFalse(DataSaverPolicy.Disabled.active.value)
    }

    @Test
    fun `data saver only engages when opt in and metered`() {
        assertFalse("OFF network metered", isDataSaverActive(optIn = false, metered = false))
        assertFalse("OFF network metered", isDataSaverActive(optIn = false, metered = true))
        assertFalse("ON but Wi-Fi (unmetered) must be unchanged", isDataSaverActive(optIn = true, metered = false))
        assertTrue("ON on a metered network", isDataSaverActive(optIn = true, metered = true))
    }
}
