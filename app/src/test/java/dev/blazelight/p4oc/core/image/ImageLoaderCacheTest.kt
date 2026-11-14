package dev.blazelight.p4oc.core.image

import coil.request.Options
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.nio.ByteBuffer

class ImageLoaderCacheTest {

    private val options = mockk<Options>(relaxed = true)
    private val keyer = ByteBufferContentKeyer()

    @Test
    fun `identical image bytes produce the same memory cache key`() {
        val bytes = ByteArray(4096) { (it * 31).toByte() }

        val first = keyer.key(ByteBuffer.wrap(bytes), options)
        val second = keyer.key(ByteBuffer.wrap(bytes.copyOf()), options)

        assertNotNull(first)
        assertEquals(first, second)
    }

    @Test
    fun `different image bytes produce different memory cache keys`() {
        val first = keyer.key(ByteBuffer.wrap(byteArrayOf(1, 2, 3)), options)
        val second = keyer.key(ByteBuffer.wrap(byteArrayOf(1, 2, 4)), options)

        assertNotNull(first)
        assertNotNull(second)
        assertNotEquals(first, second)
    }

    @Test
    fun `keying does not consume the source buffer`() {
        val buffer = ByteBuffer.wrap(ByteArray(64) { 7 })

        keyer.key(buffer, options)

        assertEquals(0, buffer.position())
        assertEquals(64, buffer.remaining())
    }

    @Test
    fun `memory cache budget is bounded to 8 MiB`() {
        assertEquals(8 * 1024 * 1024, IMAGE_MEMORY_CACHE_BYTES)
    }
}
