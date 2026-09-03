package com.pantry.organiser.core.network

import com.pantry.organiser.core.model.BatchPayload
import com.pantry.organiser.core.model.ScannedItem
import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.serialization.kotlinx.json.*
import app.cash.turbine.test
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import android.util.Log

class PocketBaseSyncServiceTest {
    
    private val pantryId = "test-pantry-123"
    private val baseUrl = "http://localhost:8090"

    @Before
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.d(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>(), any()) } returns 0
        every { Log.i(any<String>(), any<String>()) } returns 0
        every { Log.w(any<String>(), any<String>()) } returns 0
        every { Log.v(any<String>(), any<String>()) } returns 0
    }

    @After
    fun tearDown() {
        unmockkStatic(Log::class)
    }
    
    @Test
    fun `dispatch sends payload successfully`() = runTest {
        var capturedRequest: String? = null
        val mockEngine = MockEngine { request ->
            capturedRequest = (request.body as TextContent).text
            respond(
                content = "",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        val client = HttpClient(mockEngine) {
            install(ContentNegotiation) { json() }
            install(HttpTimeout)
        }
        val service = PocketBaseSyncService(client, baseUrl)
        
        val payload = BatchPayload(
            pantryId = pantryId,
            items = listOf(ScannedItem("123456789"))
        )
        
        service.dispatchBatch(payload)
        
        assertNotNull(capturedRequest)
        assert(capturedRequest!!.contains(pantryId))
        assert(capturedRequest!!.contains("123456789"))
    }

    @Test
    fun `observeUpdates performs handshake and emits payloads`() = runTest {
        val clientId = "test-client-id"
        val jsonPayload = """{"pantryId":"$pantryId","items":[{"barcode":"987654321","timestamp":1000}],"timestamp":2000}"""
        val sseContent = "data: {\"clientId\":\"$clientId\"}\ndata: {\"action\":\"create\",\"record\":$jsonPayload}\n\n"
        
        var subscriptionCaptured = false
        val mockEngine = MockEngine { request ->
            if (request.url.encodedPath == "/api/realtime" && request.method == HttpMethod.Post) {
                subscriptionCaptured = true
                respondOk()
            } else {
                respond(
                    content = sseContent,
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "text/event-stream")
                )
            }
        }
        val client = HttpClient(mockEngine) {
            install(ContentNegotiation) { json() }
            install(HttpTimeout)
        }
        val service = PocketBaseSyncService(client, baseUrl)
        
        service.observeBatches(pantryId).test {
            val payload = awaitItem()
            assertEquals(pantryId, payload.pantryId)
            assertEquals("987654321", payload.safeItems[0].barcode)
            assert(subscriptionCaptured)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
