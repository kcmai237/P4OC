package dev.blazelight.p4oc.core.network

import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

class V2CatalogCoalescingTest {

    private val json = Json

    @Test
    fun `list projects reuses the connection probe value within the TTL`() = runBlocking {
        val recorder = EndpointRecorder()
        val api = api(recorder.client(), clock = { 5_000L })

        api.listProjects(null, null)
        api.listProjects(null, null)

        assertEquals(1, recorder.count("/api/project"))
    }

    @Test
    fun `list projects refetches once the short cache expires`() = runBlocking {
        var now = 0L
        val recorder = EndpointRecorder()
        val api = api(recorder.client(), clock = { now })

        api.listProjects(null, null)
        now += 30_001L
        api.listProjects(null, null)

        assertEquals(2, recorder.count("/api/project"))
    }

    @Test
    fun `providers are fetched once per cache window`() = runBlocking {
        val recorder = EndpointRecorder()
        val api = api(recorder.client())

        api.getProviders(null, null)
        api.getProviders(null, null)

        assertEquals(1, recorder.count("/api/provider"))
        assertEquals(1, recorder.count("/api/model"))
        assertEquals(1, recorder.count("/api/model/default"))
    }

    @Test
    fun `config reads are coalesced without being retained`() = runBlocking {
        val recorder = EndpointRecorder()
        val api = api(recorder.client())

        api.getConfig(null, null)
        api.getConfig(null, null)

        assertEquals(2, recorder.count("/api/config"))
        assertEquals(2, recorder.count("/api/model/default"))
    }

    private fun api(
        client: OkHttpClient,
        clock: () -> Long = { 0L },
    ): V2WorkspaceOpenCodeApi = V2WorkspaceOpenCodeApi(
        next = mockk(relaxed = true),
        http = V2Http("http://v2.test", client, json),
        json = json,
        catalogTtlMillis = 30_000L,
        catalogClock = clock,
    )

    private class EndpointRecorder {
        private val counts = ConcurrentHashMap<String, AtomicInteger>()

        fun count(path: String): Int = counts[path]?.get() ?: 0

        fun client(): OkHttpClient = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val path = chain.request().url.encodedPath
                counts.computeIfAbsent(path) { AtomicInteger() }.incrementAndGet()
                val body = bodyFor(path).toResponseBody(JSON_MEDIA_TYPE)
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("Synthetic")
                    .body(body)
                    .build()
            }
            .build()

        private fun bodyFor(path: String): String = when (path) {
            "/api/project", "/api/config" -> "[]"
            "/api/provider", "/api/model" -> """{"data":[]}"""
            "/api/model/default" -> """{"data":null}"""
            else -> "{}"
        }
    }

    private companion object {
        val JSON_MEDIA_TYPE = "application/json".toMediaType()
    }
}
