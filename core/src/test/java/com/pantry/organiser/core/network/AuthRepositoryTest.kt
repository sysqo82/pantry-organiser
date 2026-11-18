package com.pantry.organiser.core.network

import android.content.Context
import android.content.SharedPreferences
import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.serialization.kotlinx.json.*
import io.mockk.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class AuthRepositoryTest {

    private lateinit var mockContext: Context
    private lateinit var mockPrefs: SharedPreferences
    private lateinit var mockEditor: SharedPreferences.Editor

    private val baseUrl = "https://mock.pocketbase"
    private val dayMs = 24 * 60 * 60 * 1000L
    private var now = 100 * dayMs

    companion object {
        @org.junit.BeforeClass
        @JvmStatic
        fun setupClass() {
            mockkStatic(android.util.Log::class)
            every { android.util.Log.d(any<String>(), any<String>()) } returns 0
            every { android.util.Log.e(any<String>(), any<String>()) } returns 0
            every { android.util.Log.e(any<String>(), any<String>(), any()) } returns 0
            every { android.util.Log.i(any<String>(), any<String>()) } returns 0
            every { android.util.Log.w(any<String>(), any<String>()) } returns 0
            every { android.util.Log.w(any<String>(), any<String>(), any()) } returns 0
            every { android.util.Log.v(any<String>(), any<String>()) } returns 0
        }
    }

    @Before
    fun setup() {
        mockContext = mockk()
        mockPrefs = mockk()
        mockEditor = mockk(relaxed = true)

        every { mockContext.getSharedPreferences(any(), any()) } returns mockPrefs
        every { mockPrefs.edit() } returns mockEditor
        every { mockEditor.putLong(any(), any()) } returns mockEditor
        every { mockEditor.putString(any(), any()) } returns mockEditor
        every { mockEditor.remove(any()) } returns mockEditor
        every { mockPrefs.getLong("pb_auth_last_active_at", -1L) } returns -1L
    }
    
    @org.junit.After
    fun tearDown() {
        // Keep Log mocked across async dispatcher coroutines
    }

    private fun createRepository(mockEngine: MockEngine): AuthRepository {
        val client = HttpClient(mockEngine) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }
        return AuthRepository(client, baseUrl, mockContext, clock = { now })
    }

    @Test
    fun `initial state is logged out when no token exists`() = runTest {
        every { mockPrefs.getString("pb_auth_token", null) } returns null
        val repo = createRepository(MockEngine { respondOk() })
        
        assertFalse(repo.isLoggedIn.first())
        assertNull(repo.getToken())
    }

    @Test
    fun `initial state is logged in when token exists`() = runTest {
        every { mockPrefs.getString("pb_auth_token", null) } returns "existing_token"
        val repo = createRepository(MockEngine { respondOk() })
        
        assertTrue(repo.isLoggedIn.first())
        assertEquals("existing_token", repo.getToken())
    }

    @Test
    fun `successful login saves token and updates state`() = runTest {
        every { mockPrefs.getString("pb_auth_token", null) } returns null
        
        val engine = MockEngine { request ->
            assertEquals("$baseUrl/api/collections/users/auth-with-password", request.url.toString())
            respond(
                content = """{"token": "new_fake_token", "record": {"id": "1", "email": "test@test.com"}}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        val repo = createRepository(engine)

        val result = repo.login("test@test.com", "password")
        
        assertTrue(result.isSuccess)
        verify { mockEditor.putString("pb_auth_token", "new_fake_token") }
        assertTrue(repo.isLoggedIn.first())
    }

    @Test
    fun `failed login does not save token`() = runTest {
        every { mockPrefs.getString("pb_auth_token", null) } returns null
        
        val engine = MockEngine { 
            respond(
                content = "{}",
                status = HttpStatusCode.BadRequest,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        val repo = createRepository(engine)

        val result = repo.login("test@test.com", "wrong_password")
        
        assertTrue(result.isFailure)
        assertFalse(repo.isLoggedIn.first())
    }

    @Test
    fun `logout removes token and updates state`() = runTest {
        every { mockPrefs.getString("pb_auth_token", null) } returns "existing_token"
        val repo = createRepository(MockEngine { respondOk() })
        
        repo.logout()
        
        verify { mockEditor.remove("pb_auth_token") }
        // SharedPreferences.Editor mock applies internally if set up that way, but let's verify apply was invoked or just skip this if not necessary.
        assertFalse(repo.isLoggedIn.first())
    }

    @Test
    fun `session unused for a month is logged out on startup`() = runTest {
        var storedToken: String? = "existing_token"
        every { mockPrefs.getString("pb_auth_token", null) } answers { storedToken }
        every { mockPrefs.getLong("pb_auth_last_active_at", -1L) } returns now - 30 * dayMs
        every { mockEditor.remove("pb_auth_token") } answers {
            storedToken = null
            mockEditor
        }

        val repo = createRepository(MockEngine { respondOk() })

        assertFalse(repo.isLoggedIn.first())
        assertNull(repo.getToken())
        verify { mockEditor.remove("pb_auth_last_active_at") }
    }

    @Test
    fun `entering the app within a month keeps the session and renews the window`() = runTest {
        stubStoredSession(lastActiveAt = now - 29 * dayMs)
        val repo = createRepository(MockEngine { respondOk() })

        repo.onAppEntered()

        assertTrue(repo.isLoggedIn.first())
        verify { mockEditor.putLong("pb_auth_last_active_at", now) }
        verify(exactly = 0) { mockEditor.remove("pb_auth_token") }
    }

    @Test
    fun `entering the app after a month in the background logs out`() = runTest {
        stubStoredSession(lastActiveAt = now)
        val repo = createRepository(MockEngine { respondOk() })

        now += 30 * dayMs
        repo.onAppEntered()

        assertFalse(repo.isLoggedIn.first())
    }

    @Test
    fun `existing session without activity history starts a new window`() = runTest {
        every { mockPrefs.getString("pb_auth_token", null) } returns "existing_token"

        val repo = createRepository(MockEngine { respondOk() })

        assertTrue(repo.isLoggedIn.first())
        verify { mockEditor.putLong("pb_auth_last_active_at", now) }
    }

    @Test
    fun `heartbeat keeps an always-on foreground device logged in`() = runTest {
        stubStoredSession(lastActiveAt = now)
        val repo = createRepository(MockEngine { respondOk() })
        repo.onAppEntered()

        now += 45 * dayMs
        repo.runHeartbeat()

        assertTrue(repo.isLoggedIn.first())
        verify { mockEditor.putLong("pb_auth_last_active_at", now) }
    }

    @Test
    fun `heartbeat logs out a backgrounded session after a month`() = runTest {
        stubStoredSession(lastActiveAt = now)
        val repo = createRepository(MockEngine { respondOk() })

        now += 30 * dayMs
        repo.runHeartbeat()

        assertFalse(repo.isLoggedIn.first())
    }

    @Test
    fun `rejected request keeps session when the token can still be refreshed`() = runTest {
        stubStoredSession(lastActiveAt = now)
        val repo = createRepository(MockEngine { respondWithToken("renewed_token") })

        repo.onRequestRejected()

        assertTrue(repo.isLoggedIn.first())
        verify { mockEditor.putString("pb_auth_token", "renewed_token") }
    }

    @Test
    fun `rejected request logs out when the server refuses to refresh the token`() = runTest {
        stubStoredSession(lastActiveAt = now)
        val repo = createRepository(MockEngine { respondError(HttpStatusCode.Unauthorized) })

        repo.onRequestRejected()

        assertFalse(repo.isLoggedIn.first())
    }

    @Test
    fun `rejected request keeps session when the server is unavailable`() = runTest {
        stubStoredSession(lastActiveAt = now)
        val repo = createRepository(MockEngine { respondError(HttpStatusCode.BadGateway) })

        repo.onRequestRejected()

        assertTrue(repo.isLoggedIn.first())
    }

    @Test
    fun `repeated rejections only check the session once a minute`() = runTest {
        stubStoredSession(lastActiveAt = now)
        var refreshCalls = 0
        val repo = createRepository(MockEngine {
            refreshCalls++
            respondWithToken("renewed_token")
        })

        repo.onRequestRejected()
        repo.onRequestRejected()
        assertEquals(1, refreshCalls)

        now += 60_000L
        repo.onRequestRejected()
        assertEquals(2, refreshCalls)
    }

    @Test
    fun `refresh finishing after logout does not restore the session`() = runTest {
        var storedToken: String? = "existing_token"
        every { mockPrefs.getString("pb_auth_token", null) } answers { storedToken }
        lateinit var repo: AuthRepository
        repo = createRepository(MockEngine {
            storedToken = null
            repo.logout()
            respondWithToken("late_token")
        })

        repo.refreshAuthTokenIfNeeded()

        assertFalse(repo.isLoggedIn.first())
        verify(exactly = 0) { mockEditor.putString("pb_auth_token", "late_token") }
    }

    @Test
    fun `login sends the email in lowercase without surrounding spaces`() = runTest {
        every { mockPrefs.getString("pb_auth_token", null) } returns null
        val identities = mutableListOf<String>()
        val repo = createRepository(MockEngine { request ->
            identities += identityOf(request.body)
            respondWithToken("new_token")
        })

        val result = repo.login("  Admin@LockPC.co.uk ", "Pass Word")

        assertTrue(result.isSuccess)
        assertEquals(listOf("admin@lockpc.co.uk"), identities)
    }

    @Test
    fun `login keeps the password exactly as typed`() = runTest {
        every { mockPrefs.getString("pb_auth_token", null) } returns null
        var body = ""
        val repo = createRepository(MockEngine { request ->
            body = (request.body as TextContent).text
            respondWithToken("new_token")
        })

        repo.login("Admin@lockpc.co.uk", " MixedCase ")

        assertTrue(body.contains("\" MixedCase \""))
    }

    @Test
    fun `login falls back to the typed email for accounts stored with capitals`() = runTest {
        every { mockPrefs.getString("pb_auth_token", null) } returns null
        val identities = mutableListOf<String>()
        val repo = createRepository(MockEngine { request ->
            val identity = identityOf(request.body)
            identities += identity
            if (identity == "Admin@lockpc.co.uk") respondWithToken("new_token")
            else respondError(HttpStatusCode.BadRequest)
        })

        val result = repo.login("Admin@lockpc.co.uk", "password")

        assertTrue(result.isSuccess)
        assertEquals(listOf("admin@lockpc.co.uk", "Admin@lockpc.co.uk"), identities)
    }

    @Test
    fun `lowercase email with a wrong password is only tried once`() = runTest {
        every { mockPrefs.getString("pb_auth_token", null) } returns null
        var calls = 0
        val repo = createRepository(MockEngine {
            calls++
            respondError(HttpStatusCode.BadRequest)
        })

        val result = repo.login("admin@lockpc.co.uk", "wrong")

        assertTrue(result.isFailure)
        assertEquals(1, calls)
    }

    private fun identityOf(body: Any): String =
        Json.parseToJsonElement((body as TextContent).text).jsonObject.getValue("identity").jsonPrimitive.content

    private fun stubStoredSession(lastActiveAt: Long) {
        every { mockPrefs.getString("pb_auth_token", null) } returns "existing_token"
        every { mockPrefs.getLong("pb_auth_last_active_at", -1L) } returns lastActiveAt
    }

    private fun MockRequestHandleScope.respondWithToken(token: String) = respond(
        content = """{"token": "$token", "record": {"id": "1", "email": "test@test.com"}}""",
        status = HttpStatusCode.OK,
        headers = headersOf(HttpHeaders.ContentType, "application/json")
    )
}
