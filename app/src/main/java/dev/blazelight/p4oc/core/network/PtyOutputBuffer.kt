package dev.blazelight.p4oc.core.network

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ClosedSendChannelException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.runBlocking

/**
 * Bounded handoff between OkHttp's PTY reader thread and the terminal collector.
 *
 * Frames are never dropped: once [capacity] frames are queued, [emit] suspends
 * (blocking the reader thread and therefore applying TCP backpressure to the
 * server) until the collector catches up. Retained memory is bounded to
 * [capacity] frames and ordering is preserved.
 */
internal class PtyOutputBuffer(capacity: Int = DEFAULT_CAPACITY) {
    init {
        require(capacity > 0) { "PTY output buffer capacity must be positive" }
    }

    private val channel = Channel<String>(capacity)

    val frames: Flow<String> = channel.receiveAsFlow()

    /** Suspends while the buffer is full. Never drops a frame. */
    suspend fun emit(frame: String) {
        try {
            channel.send(frame)
        } catch (_: ClosedSendChannelException) {
            // The client was torn down; a frame that arrives after close is no longer live output.
        }
    }

    /** Blocking bridge for OkHttp's reader thread, where suspending is impossible. */
    fun emitBlocking(frame: String) {
        runBlocking { emit(frame) }
    }

    fun close() {
        channel.close()
    }

    companion object {
        const val DEFAULT_CAPACITY = 1000
    }
}
