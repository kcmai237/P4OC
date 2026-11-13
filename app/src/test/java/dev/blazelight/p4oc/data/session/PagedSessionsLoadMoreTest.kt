package dev.blazelight.p4oc.data.session

import dev.blazelight.p4oc.data.remote.dto.SessionDto
import dev.blazelight.p4oc.fakes.FakeWorkspaceClient
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PagedSessionsLoadMoreTest {

    @Test
    fun `initial hydrate bounds the scope to the first page`() = runTest {
        val client = FakeWorkspaceClient().apply {
            sessionsByDirectory = mapOf(null to sessions(120))
        }
        val repository = repository(client)

        val result = repository.awaitOrFetch()

        assertTrue(result.isSuccess)
        assertEquals(50, result.getOrThrow().snapshot.sessions.size)
        assertEquals(50, client.listSessionsCallsLog.single().limit)
        assertTrue(repository.hasMoreSessions)
    }

    @Test
    fun `load more reaches the complete history without losing sessions`() = runTest {
        val client = FakeWorkspaceClient().apply {
            sessionsByDirectory = mapOf(null to sessions(120))
        }
        val repository = repository(client)
        repository.awaitOrFetch()

        assertTrue(repository.loadMoreSessions().getOrThrow())
        assertEquals(100, repository.state.value.snapshot.sessions.size)
        assertEquals(100, client.listSessionsCallsLog.last().limit)

        assertFalse(repository.loadMoreSessions().getOrThrow())
        assertEquals(120, repository.state.value.snapshot.sessions.size)
        assertEquals(
            (1..120).map { "s$it" }.toSet(),
            repository.state.value.snapshot.sessions.keys,
        )

        val calls = client.listSessionsCallsLog.size
        assertFalse(repository.loadMoreSessions().getOrThrow())
        assertEquals(calls, client.listSessionsCallsLog.size)
    }

    @Test
    fun `load more stops once every scope returns a short page`() = runTest {
        val client = FakeWorkspaceClient().apply {
            projects = listOf(FakeWorkspaceClient.projectDto("p1", "/repo/p1"))
            sessionsByDirectory = mapOf(
                null to sessions(50),
                "/repo/p1" to sessions(30, prefix = "p"),
            )
        }
        val repository = repository(client)
        repository.awaitOrFetch()

        assertTrue(repository.hasMoreSessions)
        assertFalse(repository.loadMoreSessions().getOrThrow())
        assertEquals(80, repository.state.value.snapshot.sessions.size)
        assertFalse(repository.hasMoreSessions)
    }

    private fun TestScope.repository(client: FakeWorkspaceClient) = SessionRepositoryImpl(
        client,
        nowMs = { testScheduler.currentTime },
        dispatcher = StandardTestDispatcher(testScheduler),
    )

    private fun sessions(count: Int, prefix: String = "s"): List<SessionDto> =
        (1..count).map { index ->
            FakeWorkspaceClient.sessionDto(id = "$prefix$index", updatedAt = index.toLong())
        }
}
