package dev.blazelight.p4oc.core.image

import android.content.Context
import coil.ImageLoader
import coil.key.Keyer
import coil.memory.MemoryCache
import coil.request.Options
import java.nio.ByteBuffer
import java.security.MessageDigest

internal const val IMAGE_MEMORY_CACHE_BYTES: Int = 8 * 1024 * 1024

private const val KEY_COPY_CHUNK_BYTES = 8 * 1024
private const val DIGEST_ALGORITHM = "SHA-256"
private val HEX_DIGITS = "0123456789abcdef".toCharArray()

internal fun boundedImageLoader(context: Context): ImageLoader =
    ImageLoader.Builder(context)
        .memoryCache {
            MemoryCache.Builder(context)
                .maxSizeBytes(IMAGE_MEMORY_CACHE_BYTES)
                .build()
        }
        .diskCache(null)
        .components { add(ByteBufferContentKeyer()) }
        .build()

internal class ByteBufferContentKeyer : Keyer<ByteBuffer> {
    override fun key(data: ByteBuffer, options: Options): String? {
        val digest = MessageDigest.getInstance(DIGEST_ALGORITHM)
        val source = data.asReadOnlyBuffer()
        val chunk = ByteArray(KEY_COPY_CHUNK_BYTES)
        while (source.hasRemaining()) {
            val count = minOf(source.remaining(), chunk.size)
            source.get(chunk, 0, count)
            digest.update(chunk, 0, count)
        }
        return digest.digest().toHexString()
    }
}

private fun ByteArray.toHexString(): String {
    val output = CharArray(size * 2)
    for (index in indices) {
        val value = this[index].toInt() and 0xFF
        output[index * 2] = HEX_DIGITS[value ushr 4]
        output[index * 2 + 1] = HEX_DIGITS[value and 0x0F]
    }
    return String(output)
}
