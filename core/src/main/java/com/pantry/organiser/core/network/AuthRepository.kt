package com.pantry.organiser.core.network

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

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
    context: Context
) {
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _isLoggedIn = MutableStateFlow(getToken() != null)
    val isLoggedIn: StateFlow<Boolean> = _isLoggedIn.asStateFlow()

    fun getToken(): String? = prefs.getString(KEY_TOKEN, null)

    suspend fun login(email: String, password: String): Result<Unit> {
        return try {
            val response = client.post("$baseUrl/api/collections/users/auth-with-password") {
                contentType(ContentType.Application.Json)
                setBody(mapOf("identity" to email, "password" to password))
            }
            if (response.status.isSuccess()) {
                val authResponse: AuthResponse = response.body()
                saveToken(authResponse.token)
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

    fun logout() {
        prefs.edit().remove(KEY_TOKEN).apply()
        _isLoggedIn.value = false
    }

    private fun saveToken(token: String) {
        prefs.edit().putString(KEY_TOKEN, token).apply()
        _isLoggedIn.value = true
    }

    companion object {
        private const val TAG = "AuthRepository"
        private const val PREFS_NAME = "pantry_auth_prefs"
        private const val KEY_TOKEN = "pb_auth_token"
    }
}
