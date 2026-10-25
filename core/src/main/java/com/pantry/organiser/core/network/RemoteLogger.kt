package com.pantry.organiser.core.network

import android.os.Build
import android.util.Log
import com.pantry.organiser.core.BuildConfig
import io.ktor.client.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

@Serializable
data class ExceptionData(
    val type: String? = null,
    val value: String? = null,
    val stacktrace: String? = null
)

@Serializable
data class TelemetryEvent(
    val service: String,
    val message: String,
    val level: String,
    val environment: String,
    val exception: ExceptionData? = null,
    val tags: Map<String, String>? = null,
    val metadata: Map<String, String>? = null,
    val timestamp: String? = null
)

class RemoteLogger(
    private val client: HttpClient,
    private val telemetryUrl: String = BuildConfig.TELEMETRY_URL,
    private val telemetryKey: String = BuildConfig.TELEMETRY_KEY,
    private val serviceName: String = BuildConfig.TELEMETRY_SERVICE,
    private val environment: String = BuildConfig.ENVIRONMENT,
    dispatcher: kotlinx.coroutines.CoroutineDispatcher = Dispatchers.IO,
    installUncaughtHandler: Boolean = true
) {
    private val loggerScope = CoroutineScope(dispatcher + SupervisorJob())
    private val deviceId: String by lazy {
        try {
            "${Build.MANUFACTURER} ${Build.MODEL} (API ${Build.VERSION.SDK_INT})"
        } catch (_: Throwable) {
            "Android Device"
        }
    }

    private fun formatIsoDate(date: Date): String {
        val df = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
        df.timeZone = TimeZone.getTimeZone("UTC")
        return df.format(date)
    }

    init {
        if (installUncaughtHandler) {
            installUncaughtExceptionHandler()
        }
    }

    private fun safeLog(block: () -> Unit) {
        try {
            block()
        } catch (_: Throwable) {
            // Safe fallback when android.util.Log is unmocked in unit test environments
        }
    }

    private fun installUncaughtExceptionHandler() {
        val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                e("UncaughtException", "Unhandled exception on thread ${thread.name}: ${throwable.message}", throwable)
                Thread.sleep(500)
            } catch (_: Throwable) {
                // Swallow error in handler
            } finally {
                previousHandler?.uncaughtException(thread, throwable)
            }
        }
    }

    fun d(tag: String, message: String) {
        safeLog { Log.d(tag, message) }
        sendLog(tag, message, "debug")
    }

    fun i(tag: String, message: String) {
        safeLog { Log.i(tag, message) }
        sendLog(tag, message, "info")
    }

    fun w(tag: String, message: String, t: Throwable? = null) {
        val stackTrace = t?.stackTraceToString()
        val fullMessage = if (stackTrace != null) "$message\n$stackTrace" else message
        safeLog { Log.w(tag, fullMessage) }
        sendLog(tag, message, "warn", t)
    }

    fun e(tag: String, message: String, t: Throwable? = null) {
        val stackTrace = t?.stackTraceToString()
        val fullMessage = if (stackTrace != null) "$message\n$stackTrace" else message
        safeLog { Log.e(tag, fullMessage) }
        sendLog(tag, message, "error", t)
    }

    private fun cleanStacktrace(rawStacktrace: String): String {
        return rawStacktrace.lineSequence().map { line ->
            val trimmed = line.trim()
            if (!trimmed.startsWith("at ")) return@map trimmed
            val content = trimmed.removePrefix("at ").trim()

            val openParen = content.lastIndexOf('(')
            val closeParen = content.lastIndexOf(')')

            if (openParen != -1 && closeParen > openParen) {
                val fileLine = content.substring(openParen + 1, closeParen)
                "at $fileLine"
            } else {
                trimmed
            }
        }.joinToString("\n")
    }

    private fun sendLog(tag: String, message: String, level: String, throwable: Throwable? = null) {
        if (telemetryKey.isBlank() || telemetryUrl.isBlank()) {
            return
        }

        val formattedUrl = "${telemetryUrl.trimEnd('/')}/v1/events"
        val exceptionData = if (throwable != null) {
            ExceptionData(
                type = throwable::class.java.name,
                value = throwable.message ?: throwable.toString(),
                stacktrace = cleanStacktrace(throwable.stackTraceToString())
            )
        } else {
            ExceptionData(
                type = tag,
                value = message,
                stacktrace = null
            )
        }

        val event = TelemetryEvent(
            service = serviceName,
            message = message,
            level = level,
            environment = environment,
            exception = exceptionData,
            tags = mapOf(
                "tag" to tag,
                "device_id" to deviceId,
                "build_type" to if (BuildConfig.DEBUG) "debug" else "release"
            ),
            timestamp = formatIsoDate(Date())
        )

        loggerScope.launch {
            try {
                client.post(formattedUrl) {
                    header("X-Api-Key", telemetryKey)
                    contentType(ContentType.Application.Json)
                    timeout {
                        requestTimeoutMillis = 3000
                        connectTimeoutMillis = 3000
                    }
                    setBody(event)
                }
            } catch (t: Throwable) {
                // Ignore telemetry errors to prevent loops
                safeLog { Log.e("RemoteLogger", "Failed to send telemetry event: ${t.message}") }
            }
        }
    }
}
