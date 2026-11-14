package dev.blazelight.p4oc.core.network

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PtyTerminalClientTest {

    @Test
    fun `bounded handoff never drops and preserves order under saturation`() = runTest {
        val buffer = PtyOutputBuffer(capacity = 16)
        val received = mutableListOf<String>()
        backgroundScope.launch(start = CoroutineStart.UNDISPATCHED) {
            buffer.frames.collect { received += it }
        }

        repeat(5_000) { buffer.emit("frame-$it") }
        advanceUntilIdle()

        assertEquals(5_000, received.size)
        assertEquals((0 until 5_000).map { "frame-$it" }, received)
    }

    @Test
    fun `producer suspends while the buffer is full until the consumer drains`() = runTest {
        val buffer = PtyOutputBuffer(capacity = 2)
        var emitted = 0
        val producer = launch {
            repeat(10) {
                buffer.emit("frame-$it")
                emitted += 1
            }
        }

        runCurrent()
        assertEquals(2, emitted)
        assertTrue(producer.isActive)

        val received = mutableListOf<String>()
        backgroundScope.launch(start = CoroutineStart.UNDISPATCHED) {
            buffer.frames.collect { received += it }
        }
        advanceUntilIdle()

        assertEquals(10, emitted)
        assertEquals((0 until 10).map { "frame-$it" }, received)
    }

    @Test
    fun `frames buffered before close are delivered and the stream completes`() = runTest {
        val buffer = PtyOutputBuffer(capacity = 4)
        buffer.emit("a")
        buffer.emit("b")
        buffer.close()

        // A frame received after teardown must be a no-op, never an exception.
        buffer.emit("c")

        assertEquals(listOf("a", "b"), buffer.frames.toList())
    }

    @Test
    fun `a frame is delivered before the producer fills the buffer`() = runTest {
        val buffer = PtyOutputBuffer(capacity = 2)
        val first = CompletableDeferred<String>()
        backgroundScope.launch(start = CoroutineStart.UNDISPATCHED) {
            buffer.frames.collect {
                if (!first.isCompleted) first.complete(it)
            }
        }

        buffer.emit("hello")

        assertEquals("hello", first.await())
    }
}
