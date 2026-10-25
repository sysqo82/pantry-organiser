package com.pantry.organiser.core.network

import android.util.Log
import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import io.mockk.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RemoteLoggerTest {

    @Before
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.d(any<String>(), any<String>()) } returns 0
        every { Log.i(any<String>(), any<String>()) } returns 0
        every { Log.w(any<String>(), any<String>()) } returns 0
        every { Log.w(any<String>(), any<String>(), any()) } returns 0
        every { Log.e(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>(), any()) } returns 0
    }

    @After
    fun tearDown() {
        unmockkStatic(Log::class)
    }

    @Test
    fun `test direct serialization of TelemetryEvent`() {
        val event = TelemetryEvent(
            service = "pantry-organiser",
            message = "test message",
            level = "error",
            environment = "debug",
            exception = ExceptionData(
                type = "java.lang.IllegalStateException",
                value = "Test crash",
                stacktrace = "at com.test.Sample(Sample.kt:1)"
            )
        )
        val jsonString = Json.encodeToString(TelemetryEvent.serializer(), event)
        assertNotNull(jsonString)
        assertTrue(jsonString.contains("java.lang.IllegalStateException"))
    }

    private fun createTestClient(engine: MockEngine): HttpClient {
        return HttpClient(engine) {
            install(ContentNegotiation) {
                json(Json {
                    ignoreUnknownKeys = true
                    explicitNulls = false
                    encodeDefaults = true
                })
            }
            install(HttpTimeout)
        }
    }

    private fun OutgoingContent.extractText(): String {
        return when (this) {
            is TextContent -> text
            is OutgoingContent.ByteArrayContent -> bytes().decodeToString()
            else -> ""
        }
    }

    private fun waitForCondition(timeoutMs: Long = 3000, condition: () -> Boolean) {
        val start = System.currentTimeMillis()
        while (!condition() && (System.currentTimeMillis() - start) < timeoutMs) {
            Thread.sleep(20)
        }
    }

    @Test
    fun `sendLog sends formatted event to telemetry endpoint`() = runTest {
        var capturedUrl: String? = null
        var capturedApiKey: String? = null
        var capturedBodyText: String? = null

        val mockEngine = MockEngine { request ->
            capturedUrl = request.url.toString()
            capturedApiKey = request.headers["X-Api-Key"]
            capturedBodyText = request.body.extractText()
            respond("{\"status\":\"accepted\"}", HttpStatusCode.Accepted, headersOf(HttpHeaders.ContentType, "application/json"))
        }

        val client = createTestClient(mockEngine)
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        val logger = RemoteLogger(
            client = client,
            telemetryUrl = "https://telemetry.lockpc.co.uk",
            telemetryKey = "tm_test_key_123",
            serviceName = "pantry-organiser-test",
            environment = "debug",
            dispatcher = testDispatcher,
            installUncaughtHandler = false
        )

        logger.i("TestTag", "Hello Telemetry")
        waitForCondition { capturedUrl != null && capturedBodyText != null }

        assertEquals("https://telemetry.lockpc.co.uk/v1/events", capturedUrl)
        assertEquals("tm_test_key_123", capturedApiKey)

        assertNotNull(capturedBodyText)
        val json = Json.parseToJsonElement(capturedBodyText!!).jsonObject
        assertEquals("pantry-organiser-test", json["service"]?.jsonPrimitive?.content)
        assertEquals("Hello Telemetry", json["message"]?.jsonPrimitive?.content)
        assertEquals("info", json["level"]?.jsonPrimitive?.content)
        assertEquals("debug", json["environment"]?.jsonPrimitive?.content)
        assertEquals("TestTag", json["exception"]?.jsonObject?.get("type")?.jsonPrimitive?.content)
    }

    @Test
    fun `sendLog with exception includes exception type and stacktrace`() = runTest {
        var capturedBodyText: String? = null

        val mockEngine = MockEngine { request ->
            capturedBodyText = request.body.extractText()
            respond("{\"status\":\"accepted\"}", HttpStatusCode.Accepted, headersOf(HttpHeaders.ContentType, "application/json"))
        }

        val client = createTestClient(mockEngine)
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        val logger = RemoteLogger(
            client = client,
            telemetryUrl = "https://telemetry.lockpc.co.uk",
            telemetryKey = "tm_test_key_123",
            serviceName = "pantry-organiser-test",
            environment = "production",
            dispatcher = testDispatcher,
            installUncaughtHandler = false
        )

        val testException = IllegalStateException("Test crash message")
        logger.e("TestTag", "Something went wrong", testException)
        waitForCondition { capturedBodyText != null }

        assertNotNull(capturedBodyText)
        val json = Json.parseToJsonElement(capturedBodyText!!).jsonObject
        assertEquals("error", json["level"]?.jsonPrimitive?.content)
        assertEquals("Something went wrong", json["message"]?.jsonPrimitive?.content)

        val excObj = json["exception"]?.jsonObject
        assertNotNull(excObj)
        assertEquals("java.lang.IllegalStateException", excObj!!["type"]?.jsonPrimitive?.content)
        assertEquals("Test crash message", excObj["value"]?.jsonPrimitive?.content)
        assertTrue(excObj["stacktrace"]?.jsonPrimitive?.content?.contains("IllegalStateException: Test crash message") == true)
    }

    @Test
    fun `sendLog is no-op when telemetry key is blank`() = runTest {
        var requestMade = false

        val mockEngine = MockEngine { _ ->
            requestMade = true
            respond("{}", HttpStatusCode.OK)
        }

        val client = createTestClient(mockEngine)
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        val logger = RemoteLogger(
            client = client,
            telemetryUrl = "https://telemetry.lockpc.co.uk",
            telemetryKey = "",
            serviceName = "pantry-organiser",
            environment = "debug",
            dispatcher = testDispatcher,
            installUncaughtHandler = false
        )

        logger.e("Tag", "Error message")
        Thread.sleep(200)

        assertFalse("Request should not be sent if telemetry key is blank", requestMade)
    }
}
