package dev.blazelight.p4oc.data.session

import dev.blazelight.p4oc.core.network.ConnectionState
import dev.blazelight.p4oc.core.network.OpenCodeApi
import dev.blazelight.p4oc.data.remote.dto.MessageInfoDto
import dev.blazelight.p4oc.data.remote.dto.MessageTimeDto
import dev.blazelight.p4oc.data.remote.dto.MessageWrapperDto
import dev.blazelight.p4oc.data.remote.mapper.MessageMapper
import dev.blazelight.p4oc.data.server.ActiveServerApiProvider
import dev.blazelight.p4oc.data.workspace.WorkspaceClient
import dev.blazelight.p4oc.domain.model.Message
import dev.blazelight.p4oc.domain.model.OpenCodeEvent
import dev.blazelight.p4oc.domain.model.TokenUsage
import dev.blazelight.p4oc.domain.server.ServerGeneration
import dev.blazelight.p4oc.domain.server.ServerRef
import dev.blazelight.p4oc.domain.session.SessionId
import dev.blazelight.p4oc.domain.workspace.Workspace
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MessageWindowPagingTest {

    private val sessionId = SessionId("s1")
    private val mapper = MessageMapper()

    // Ascending server history m1..m350; the newest page is the tail.
    private val history = (1L..350L).map { assistantWrapper("m$it", createdAt = it) }

    @Test
    fun `incremental paging walks the full history without duplicates`() = runTest {
        val (repo, api) = repository(testScheduler, incremental = true)
        coEvery { api.getMessages(any(), any(), any(), any(), any()) } coAnswers {
            page(secondArg<Int?>(), thirdArg<String?>())
        }
        val lease = repo.acquireSession(sessionId)

        assertEquals(100, repo.loadMessages(sessionId, 100))
        assertEquals(200, repo.loadMessages(sessionId, 200))
        assertEquals(300, repo.loadMessages(sessionId, 300))
        assertEquals(350, repo.loadMessages(sessionId, 400))
        advanceUntilIdle()

        val ids = repo.messages(sessionId).value.map { it.message.id }
        assertEquals((1L..350L).map { "m$it" }, ids)
        assertEquals(ids.size, ids.toSet().size)

        // Each grow step fetches only the missing older page, never a fresh full window.
        coVerify(exactly = 1) { api.getMessages("s1", 100, "m251", "/test", null) }
        coVerify(exactly = 1) { api.getMessages("s1", 100, "m151", "/test", null) }
        coVerify(exactly = 1) { api.getMessages("s1", 100, "m51", "/test", null) }
        coVerify(exactly = 0) { api.getMessages("s1", 200, null, "/test", null) }
        coVerify(exactly = 0) { api.getMessages("s1", 300, null, "/test", null) }
        coVerify(exactly = 0) { api.getMessages("s1", 400, null, "/test", null) }
        lease.close()
    }

    @Test
    fun `server ignoring the cursor falls back to a full window fetch`() = runTest {
        val (repo, api) = repository(testScheduler, incremental = true)
        coEvery { api.getMessages(any(), any(), any(), any(), any()) } coAnswers {
            // Ignore the cursor: always return the newest window, which re-includes known ids.
            history.takeLast(secondArg<Int?>() ?: history.size)
        }
        val lease = repo.acquireSession(sessionId)

        assertEquals(100, repo.loadMessages(sessionId, 100))
        assertEquals(200, repo.loadMessages(sessionId, 200))
        advanceUntilIdle()

        val ids = repo.messages(sessionId).value.map { it.message.id }
        assertEquals((151L..350L).map { "m$it" }, ids)
        // Cursor attempt first, then the fallback full window.
        coVerify(exactly = 1) { api.getMessages("s1", 100, "m251", "/test", null) }
        coVerify(exactly = 1) { api.getMessages("s1", 200, null, "/test", null) }
        lease.close()
    }

    @Test
    fun `incremental paging matches the non incremental window`() = runTest {
        val (onRepo, onApi) = repository(testScheduler, incremental = true)
        val (offRepo, offApi) = repository(testScheduler, incremental = false)
        coEvery { onApi.getMessages(any(), any(), any(), any(), any()) } coAnswers {
            page(secondArg<Int?>(), thirdArg<String?>())
        }
        coEvery { offApi.getMessages(any(), any(), any(), any(), any()) } coAnswers {
            page(secondArg<Int?>(), thirdArg<String?>())
        }

        val onLease = onRepo.acquireSession(sessionId)
        onRepo.loadMessages(sessionId, 100)
        onRepo.loadMessages(sessionId, 200)
        onRepo.loadMessages(sessionId, 300)
        onRepo.loadMessages(sessionId, 400)

        val offLease = offRepo.acquireSession(sessionId)
        offRepo.loadMessages(sessionId, 400)
        advanceUntilIdle()

        val onIds = onRepo.messages(sessionId).value.map { it.message.id }
        val offIds = offRepo.messages(sessionId).value.map { it.message.id }
        assertEquals(offIds, onIds)
        assertEquals((1L..350L).map { "m$it" }, onIds)
        onLease.close()
        offLease.close()
    }

    @Test
    fun `paging survives reconnect and live prompt without gaps or duplicates`() = runTest {
        val (repo, api) = repository(testScheduler, incremental = true)
        coEvery { api.getMessages(any(), any(), any(), any(), any()) } coAnswers {
            page(secondArg<Int?>(), thirdArg<String?>())
        }
        val lease = repo.acquireSession(sessionId)

        repo.loadMessages(sessionId, 100)
        repo.loadMessages(sessionId, 200)
        repo.loadMessages(sessionId, 300)
        repo.loadMessages(sessionId, 400)
        advanceUntilIdle()

        // A live prompt response lands via SSE after the bounded history was loaded.
        repo.acceptEvent(OpenCodeEvent.MessageUpdated(assistantMessage("m351", createdAt = 351L)))
        advanceUntilIdle()

        // Reconnect recovery re-fetches the remembered full window non-destructively.
        repo.acceptEvent(OpenCodeEvent.Connected)
        advanceUntilIdle()

        val ids = repo.messages(sessionId).value.map { it.message.id }
        assertEquals((1L..351L).map { "m$it" }, ids)
        assertEquals(ids.size, ids.toSet().size)
        // The prompt response must survive the recovery, which refetches exactly the loaded window.
        coVerify(exactly = 1) { api.getMessages("s1", 400, null, "/test", null) }
        lease.close()
    }

    private fun page(limit: Int?, before: String?): List<MessageWrapperDto> {
        val size = limit ?: history.size
        if (before == null) return history.takeLast(size)
        val index = history.indexOfFirst { it.info.id == before }
        if (index < 0) return history.takeLast(size)
        val start = (index - size).coerceAtLeast(0)
        return history.subList(start, index)
    }

    private fun repository(
        scheduler: TestCoroutineScheduler,
        incremental: Boolean,
    ): Pair<SessionRepositoryImpl, OpenCodeApi> {
        val api = mockk<OpenCodeApi>(relaxed = true)
        val repo = SessionRepositoryImpl(
            clientFor(api),
            messageMapper = mapper,
            dispatcher = StandardTestDispatcher(scheduler),
            incrementalMessages = incremental,
        )
        return repo to api
    }

    private fun clientFor(api: OpenCodeApi): WorkspaceClient = WorkspaceClient(
        workspace = Workspace(
            server = ServerRef.fromEndpointKey("http://test.local"),
            directory = "/test",
        ),
        generation = ServerGeneration(0L),
        apiProvider = ActiveServerApiProvider { _, _ -> api },
        connectionState = MutableStateFlow(ConnectionState.Disconnected),
    )

    private fun assistantWrapper(
        id: String,
        createdAt: Long,
    ): MessageWrapperDto = MessageWrapperDto(
        info = MessageInfoDto(
            id = id,
            sessionID = "s1",
            time = MessageTimeDto(created = createdAt),
            role = "assistant",
            parentID = "",
            providerID = "provider",
            modelID = "model",
            agent = "assistant",
            mode = "chat",
        ),
        parts = emptyList(),
    )

    private fun assistantMessage(id: String, createdAt: Long): Message.Assistant = Message.Assistant(
        id = id,
        sessionID = "s1",
        createdAt = createdAt,
        parentID = "",
        providerID = "provider",
        modelID = "model",
        mode = "chat",
        agent = "assistant",
        cost = 0.0,
        tokens = TokenUsage(input = 0, output = 0),
    )
}
