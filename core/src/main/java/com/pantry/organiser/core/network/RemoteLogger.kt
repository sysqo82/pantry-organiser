package com.pantry.organiser.core.network

import android.os.Build
import android.util.Log
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Serializable
data class LogEntry(
    val timestamp: String,
    val message: String,
    val tag: String,
    val device_id: String,
    val level: String
)

class RemoteLogger(
    private val client: HttpClient,
    private val baseUrl: String,
    private val authRepository: AuthRepository?
) {
    private val loggerScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val deviceId: String by lazy {
        "${Build.MANUFACTURER} ${Build.MODEL} (API ${Build.VERSION.SDK_INT})"
    }

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
        timeZone = java.util.TimeZone.getTimeZone("UTC")
    }

    fun d(tag: String, message: String) {
        Log.d(tag, message)
        sendLog(tag, message, "DEBUG")
    }

    fun i(tag: String, message: String) {
        Log.i(tag, message)
        sendLog(tag, message, "INFO")
    }

    fun w(tag: String, message: String, t: Throwable? = null) {
        val fullMessage = if (t != null) "$message\n${Log.getStackTraceString(t)}" else message
        Log.w(tag, fullMessage)
        sendLog(tag, fullMessage, "WARN")
    }

    fun e(tag: String, message: String, t: Throwable? = null) {
        val fullMessage = if (t != null) "$message\n${Log.getStackTraceString(t)}" else message
        Log.e(tag, fullMessage)
        sendLog(tag, fullMessage, "ERROR")
    }

    private fun sendLog(tag: String, message: String, level: String) {
        val entry = LogEntry(
            timestamp = dateFormat.format(Date()),
            message = message,
            tag = tag,
            device_id = deviceId,
            level = level
        )

        loggerScope.launch {
            try {
                client.post("$baseUrl/api/collections/device_logs/records") {
                    authRepository?.getToken()?.let { token ->
                        header(HttpHeaders.Authorization, "Bearer $token")
                    }
                    contentType(ContentType.Application.Json)
                    setBody(entry)
                }
            } catch (e: Exception) {
                // If remote logging fails, just print to logcat to avoid infinite loops
                Log.e("RemoteLogger", "Failed to send log to server: ${e.message}")
            }
        }
    }
}
