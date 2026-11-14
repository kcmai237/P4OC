package dev.blazelight.p4oc.data.session

import dev.blazelight.p4oc.core.network.DataSaverPolicy
import dev.blazelight.p4oc.data.remote.mapper.SessionMapper
import dev.blazelight.p4oc.data.workspace.SessionWorkspaceClient
import dev.blazelight.p4oc.domain.model.OpenCodeEvent
import dev.blazelight.p4oc.fakes.FakeWorkspaceClient
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DataSaverRefreshTest {

    @Test
    fun `data saver off keeps the base refresh behavior`() = runTest {
        val policy = FakeDataSaverPolicy()
        val client = client()
        val repository = repository(client, policy)
        repository.awaitOrFetch()
        assertEquals(1, client.listProjectsCalls)

        repository.acceptEvent(OpenCodeEvent.ProjectRefreshRequested("project-s1"))
        advanceUntilIdle()
        assertEquals(2, client.listProjectsCalls)

        repository.acceptEvent(OpenCodeEvent.SessionRefreshRequested("s1"))
        advanceUntilIdle()
        assertEquals(1, client.getSessionCalls)
        repository.close()
    }

    @Test
    fun `data saver active defers non essential refreshes and catches up once`() = runTest {
        val policy = FakeDataSaverPolicy()
        val client = client()
        val repository = repository(client, policy)
        repository.awaitOrFetch()
        assertEquals(1, client.listProjectsCalls)

        policy.active.value = true
        advanceUntilIdle()

        repository.acceptEvent(OpenCodeEvent.ProjectRefreshRequested("project-s1"))
        repository.acceptEvent(OpenCodeEvent.SessionRefreshRequested("s1"))
        advanceUntilIdle()

        assertEquals("no hydrate while data saver is active", 1, client.listProjectsCalls)
        assertEquals("no session metadata fetch while active", 0, client.getSessionCalls)

        policy.active.value = false
        advanceUntilIdle()

        assertEquals("exactly one catch-up hydrate", 2, client.listProjectsCalls)
        repository.close()
    }

    @Test
    fun `data saver still applies live SSE session updates`() = runTest {
        val policy = FakeDataSaverPolicy()
        val client = client()
        val repository = repository(client, policy)
        repository.refresh()

        policy.active.value = true
        advanceUntilIdle()
        val updated = SessionMapper.mapToDomain(
            client.getSessionResults.getValue("s1").copy(title = "Live under data saver"),
        )
        repository.acceptEvent(OpenCodeEvent.SessionUpdated(updated))

        assertEquals(
            "Live under data saver",
            repository.state.value.snapshot.sessions.getValue("s1").session.title,
        )
        assertEquals(0, client.getSessionCalls)
        repository.close()
    }

    private class FakeDataSaverPolicy : DataSaverPolicy {
        override val active = MutableStateFlow(false)
    }

    private fun client(): FakeWorkspaceClient = FakeWorkspaceClient().apply {
        val initial = FakeWorkspaceClient.sessionDto("s1", title = "Initial")
        listSessionsResult = listOf(initial)
        getSessionResults[initial.id] = initial
    }

    private fun TestScope.repository(
        client: SessionWorkspaceClient,
        policy: DataSaverPolicy,
    ): SessionRepositoryImpl = SessionRepositoryImpl(
        client,
        nowMs = { testScheduler.currentTime },
        dispatcher = StandardTestDispatcher(testScheduler),
        dataSaverPolicy = policy,
    )
}
