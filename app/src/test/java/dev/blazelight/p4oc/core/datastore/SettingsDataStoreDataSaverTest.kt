package dev.blazelight.p4oc.core.datastore

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.mutablePreferencesOf
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsDataStoreDataSaverTest {

    @Test
    fun `data saver preference defaults to true`() {
        assertTrue(SettingsDataStore.readDataSaverEnabled(mutablePreferencesOf()))
    }

    @Test
    fun `data saver preference round trips`() {
        val enabled = mutablePreferencesOf()
        enabled[booleanPreferencesKey("data_saver_enabled")] = true
        assertTrue(SettingsDataStore.readDataSaverEnabled(enabled))

        enabled[booleanPreferencesKey("data_saver_enabled")] = false
        assertFalse(SettingsDataStore.readDataSaverEnabled(enabled))
    }
}
