package dev.blazelight.p4oc.data.session

import dev.blazelight.p4oc.core.lifecycle.MutableForegroundState
import dev.blazelight.p4oc.data.remote.mapper.SessionMapper
import dev.blazelight.p4oc.data.workspace.SessionWorkspaceClient
import dev.blazelight.p4oc.domain.model.OpenCodeEvent
import dev.blazelight.p4oc.fakes.FakeWorkspaceClient
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SessionRepositoryPauseTest {

    @Test
    fun `sse session events still apply while backgrounded`() = runTest {
        val foreground = MutableForegroundState()
        val client = client()
        val repository = repository(client, foreground)
        repository.refresh()

        foreground.setForeground(false)
        advanceUntilIdle()
        val updated = SessionMapper.mapToDomain(
            client.getSessionResults.getValue("s1").copy(title = "While background"),
        )
        repository.acceptEvent(OpenCodeEvent.SessionUpdated(updated))

        assertEquals(
            "While background",
            repository.state.value.snapshot.sessions.getValue("s1").session.title,
        )
        assertEquals(0, client.getSessionCalls)
        repository.close()
    }

    @Test
    fun `non-essential refreshes pause in background and resume once`() = runTest {
        val foreground = MutableForegroundState()
        val client = client()
        val repository = repository(client, foreground)
        repository.awaitOrFetch()
        assertEquals(1, client.listProjectsCalls)

        foreground.setForeground(false)
        advanceUntilIdle()
        repository.acceptEvent(OpenCodeEvent.ProjectRefreshRequested("project-s1"))
        repository.acceptEvent(OpenCodeEvent.SessionRefreshRequested("s1"))
        advanceUntilIdle()

        assertEquals("no hydrate while backgrounded", 1, client.listProjectsCalls)
        assertEquals("no metadata fetch while backgrounded", 0, client.getSessionCalls)

        foreground.setForeground(true)
        advanceUntilIdle()

        assertEquals("exactly one catch-up hydrate", 2, client.listProjectsCalls)

        foreground.setForeground(false)
        advanceUntilIdle()
        foreground.setForeground(true)
        advanceUntilIdle()
        assertEquals("no hydrate without pending work", 2, client.listProjectsCalls)
        repository.close()
    }

    private fun client(): FakeWorkspaceClient = FakeWorkspaceClient().apply {
        val initial = FakeWorkspaceClient.sessionDto("s1", title = "Initial")
        listSessionsResult = listOf(initial)
        getSessionResults[initial.id] = initial
    }

    private fun TestScope.repository(
        client: SessionWorkspaceClient,
        foregroundState: MutableForegroundState,
    ): SessionRepositoryImpl = SessionRepositoryImpl(
        client,
        nowMs = { testScheduler.currentTime },
        dispatcher = StandardTestDispatcher(testScheduler),
        foregroundState = foregroundState,
    )
}
