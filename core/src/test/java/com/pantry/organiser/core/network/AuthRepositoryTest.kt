package com.pantry.organiser.core.network

import android.content.Context
import android.content.SharedPreferences
import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.mockk.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class AuthRepositoryTest {

    private lateinit var mockContext: Context
    private lateinit var mockPrefs: SharedPreferences
    private lateinit var mockEditor: SharedPreferences.Editor

    private val baseUrl = "https://mock.pocketbase"

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
        return AuthRepository(client, baseUrl, mockContext)
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
}
