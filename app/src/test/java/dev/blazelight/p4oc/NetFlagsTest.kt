package dev.blazelight.p4oc

import org.junit.Assert.assertEquals
import org.junit.Test

class NetFlagsTest {

    private val enabledByPolicy = when (BuildConfig.BUILD_TYPE) {
        "release" -> false
        "debug", "githubRelease" -> true
        else -> error("Unexpected build type: ${BuildConfig.BUILD_TYPE}")
    }

    @Test
    fun `network flags follow the build type policy`() {
        assertEquals(enabledByPolicy, BuildConfig.NET_O2_INCREMENTAL_MESSAGES)
        assertEquals(enabledByPolicy, BuildConfig.NET_O3_SSE_BACKOFF)
        assertEquals(enabledByPolicy, BuildConfig.NET_O4_SHELL_POLLING)
        assertEquals(enabledByPolicy, BuildConfig.NET_O5_COIL_BOUNDED)
    }
}
