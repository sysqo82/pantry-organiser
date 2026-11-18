package com.pantry.organiser.core.network

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.Locale

@Serializable
data class AuthResponse(
    val token: String,
    val record: Record
) {
    @Serializable
    data class Record(val id: String, val email: String)
}

class AuthRepository(
    private val client: HttpClient,
    private val baseUrl: String,
    context: Context,
    private val clock: () -> Long = System::currentTimeMillis
) {
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    init {
        if (getToken() != null) {
            if (isSessionInactive()) {
                Log.i(TAG, "Session unused for too long. Clearing stored token.")
                clearStoredSession()
            } else if (prefs.getLong(KEY_LAST_ACTIVE_AT, NEVER_ACTIVE) == NEVER_ACTIVE) {
                // Sessions created before inactivity tracking start their window now.
                markSessionActive()
            }
        }
    }

    private val _isLoggedIn = MutableStateFlow(getToken() != null)
    val isLoggedIn: StateFlow<Boolean> = _isLoggedIn.asStateFlow()

    private val authScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val rejectionCheckMutex = Mutex()

    @Volatile
    private var isAppInForeground = false

    @Volatile
    private var lastRejectionCheckAt: Long? = null

    init {
        authScope.launch {
            // Continuous heartbeat for 24/7 always-on devices
            while (isActive) {
                delay(HEARTBEAT_INTERVAL_MS)
                runHeartbeat()
            }
        }
    }

    fun getToken(): String? = prefs.getString(KEY_TOKEN, null)

    /** Call when the user enters the app. Ends a session unused for a month, otherwise renews it. */
    fun onAppEntered() {
        isAppInForeground = true
        if (getToken() == null || logoutIfInactive()) return
        markSessionActive()
        authScope.launch { refreshAuthTokenIfNeeded() }
    }

    /** Call when the user leaves the app, so the inactivity window starts from this moment. */
    fun onAppLeft() {
        isAppInForeground = false
        if (getToken() != null) markSessionActive()
    }

    /**
     * Call when the server rejects a request with 401 or 403. A 403 can simply mean a collection
     * rule denied that one request, so the session only ends if the server also refuses to renew it.
     */
    suspend fun onRequestRejected() {
        if (getToken() == null) return
        rejectionCheckMutex.withLock {
            val now = clock()
            val lastCheck = lastRejectionCheckAt
            if (lastCheck != null && now - lastCheck in 0 until REJECTION_CHECK_INTERVAL_MS) return
            lastRejectionCheckAt = now
            refreshAuthTokenIfNeeded()
        }
    }

    internal suspend fun runHeartbeat() {
        if (getToken() == null) return
        if (isAppInForeground) {
            markSessionActive()
        } else if (logoutIfInactive()) {
            return
        }
        Log.d(TAG, "Running daily proactive auth token refresh...")
        refreshAuthTokenIfNeeded()
    }

    suspend fun refreshAuthTokenIfNeeded() {
        val currentToken = getToken() ?: return
        try {
            val response = client.post("$baseUrl/api/collections/users/auth-refresh") {
                header(HttpHeaders.Authorization, "Bearer $currentToken")
                contentType(ContentType.Application.Json)
            }
            if (response.status.isSuccess()) {
                val authResponse: AuthResponse = response.body()
                // The user may have logged out or switched account while this request was in flight.
                if (getToken() == currentToken) {
                    saveToken(authResponse.token)
                    Log.d(TAG, "Successfully refreshed Auth Token in background.")
                }
            } else if (response.status == HttpStatusCode.Unauthorized || response.status == HttpStatusCode.Forbidden) {
                if (getToken() == currentToken) {
                    Log.w(TAG, "Auth Token expired or invalid. Auto-logging out.")
                    logout()
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Failed to refresh auth token (could be offline). Preserving existing token.", e)
            // Note: If offline, we keep the token so the app continues to operate locally.
        }
    }

    suspend fun login(email: String, password: String): Result<Unit> {
        return try {
            val typedEmail = email.trim()
            val normalizedEmail = typedEmail.lowercase(Locale.ROOT)
            var response = requestLogin(normalizedEmail, password)
            if (response.status == HttpStatusCode.BadRequest && typedEmail != normalizedEmail) {
                // Accounts created with capitals may be stored exactly as they were typed.
                response = requestLogin(typedEmail, password)
            }
            if (response.status.isSuccess()) {
                val authResponse: AuthResponse = response.body()
                saveToken(authResponse.token)
                markSessionActive()
                lastRejectionCheckAt = null
                Result.success(Unit)
            } else {
                Log.e(TAG, "Login failed: ${response.status}")
                Result.failure(Exception("Login failed: ${response.status}"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Login error", e)
            Result.failure(e)
        }
    }

    private suspend fun requestLogin(identity: String, password: String): HttpResponse =
        client.post("$baseUrl/api/collections/users/auth-with-password") {
            contentType(ContentType.Application.Json)
            setBody(mapOf("identity" to identity, "password" to password))
        }

    fun logout() {
        clearStoredSession()
        lastRejectionCheckAt = null
        _isLoggedIn.value = false
    }

    private fun logoutIfInactive(): Boolean {
        if (!isSessionInactive()) return false
        Log.i(TAG, "Session unused for too long. Logging out.")
        logout()
        return true
    }

    private fun isSessionInactive(): Boolean {
        val lastActiveAt = prefs.getLong(KEY_LAST_ACTIVE_AT, NEVER_ACTIVE)
        return lastActiveAt != NEVER_ACTIVE && clock() - lastActiveAt >= SESSION_INACTIVITY_LIMIT_MS
    }

    private fun markSessionActive() {
        prefs.edit().putLong(KEY_LAST_ACTIVE_AT, clock()).apply()
    }

    private fun clearStoredSession() {
        prefs.edit().remove(KEY_TOKEN).remove(KEY_LAST_ACTIVE_AT).apply()
    }

    private fun saveToken(token: String) {
        prefs.edit().putString(KEY_TOKEN, token).apply()
        _isLoggedIn.value = true
    }

    companion object {
        private const val TAG = "AuthRepository"
        private const val PREFS_NAME = "pantry_auth_prefs"
        private const val KEY_TOKEN = "pb_auth_token"
        private const val KEY_LAST_ACTIVE_AT = "pb_auth_last_active_at"
        private const val NEVER_ACTIVE = -1L
        private const val HEARTBEAT_INTERVAL_MS = 24 * 60 * 60 * 1000L
        private const val REJECTION_CHECK_INTERVAL_MS = 60 * 1000L
        private const val SESSION_INACTIVITY_LIMIT_MS = 30 * 24 * 60 * 60 * 1000L
    }
}
