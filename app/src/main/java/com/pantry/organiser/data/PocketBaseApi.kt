package com.pantry.organiser.data

import com.pantry.organiser.core.model.PantryConstants
import com.pantry.organiser.core.model.PantryItem
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.utils.io.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import java.io.File

@Serializable
data class PocketBaseConnectResponse(val clientId: String)

class PocketBaseApi {
    private val baseUrl = PantryConstants.POCKETBASE_URL
    private val json = Json { 
        ignoreUnknownKeys = true 
        coerceInputValues = true
        isLenient = true
        encodeDefaults = true
        explicitNulls = false // Crucial: prevents sending "id": null which PocketBase rejects
    }
    private val client = HttpClient(OkHttp) {
        expectSuccess = true
        install(ContentNegotiation) { json(json) }
        install(HttpTimeout) {
            requestTimeoutMillis = 30000
            connectTimeoutMillis = 15000
        }
    }

    // Separate client for realtime SSE to avoid blocking the main connection pool
    private val realtimeClient = HttpClient(OkHttp) {
        install(ContentNegotiation) { json(json) }
        install(HttpTimeout) {
            requestTimeoutMillis = HttpTimeout.INFINITE_TIMEOUT_MS
            connectTimeoutMillis = 15000
        }
    }

    private var clientId: String? = null
    private val _realtimeEvents = MutableSharedFlow<RealtimeEvent>()
    val realtimeEvents: Flow<RealtimeEvent> = _realtimeEvents

    suspend fun getItems(): List<PocketBasePantryItem>? {
        return try {
            val response: PocketBaseListResponse<PocketBasePantryItem> = 
                client.get("$baseUrl/api/collections/pantry_items/records") {
                    parameter("perPage", 500)
                }.body()
            response.items
        } catch (e: Exception) {
            android.util.Log.e("PocketBase", "Failed to fetch items", e)
            null
        }
    }

    suspend fun createItem(item: PocketBasePantryItem): PocketBasePantryItem? {
        return try {
            client.post("$baseUrl/api/collections/pantry_items/records") {
                contentType(ContentType.Application.Json)
                setBody(item)
            }.body()
        } catch (e: ClientRequestException) {
            val errorBody = e.response.bodyAsText()
            android.util.Log.e("PocketBase", "Failed to create item: $errorBody", e)
            null
        } catch (e: Exception) {
            android.util.Log.e("PocketBase", "Failed to create item", e)
            null
        }
    }

    suspend fun updateItem(id: String, item: PocketBasePantryItem): PocketBasePantryItem? {
        return try {
            val jsonBody = json.encodeToString(PocketBasePantryItem.serializer(), item)
            android.util.Log.d("PocketBase", "Updating item $id with body: $jsonBody")
            
            client.patch("$baseUrl/api/collections/pantry_items/records/$id") {
                contentType(ContentType.Application.Json)
                setBody(item)
            }.body()
        } catch (e: ClientRequestException) {
            val errorBody = e.response.bodyAsText()
            android.util.Log.e("PocketBase", "Failed to update item: $errorBody", e)
            null
        } catch (e: Exception) {
            android.util.Log.e("PocketBase", "Failed to update item", e)
            null
        }
    }

    suspend fun deleteItem(id: String): Boolean {
        return try {
            client.delete("$baseUrl/api/collections/pantry_items/records/$id").status.isSuccess()
        } catch (e: Exception) {
            android.util.Log.e("PocketBase", "Failed to delete item", e)
            false
        }
    }

    suspend fun uploadImage(id: String, file: File): PocketBasePantryItem? {
        android.util.Log.d("PocketBase", "Uploading image for $id: ${file.absolutePath} (${file.length()} bytes)")
        return try {
            val responseBody = client.patch("$baseUrl/api/collections/pantry_items/records/$id") {
                setBody(MultiPartFormDataContent(
                    formData {
                        append("image", file.readBytes(), Headers.build {
                            append(HttpHeaders.ContentType, "image/jpeg")
                            append(HttpHeaders.ContentDisposition, "filename=\"${file.name}\"")
                        })
                    }
                ))
            }.bodyAsText()
            
            android.util.Log.d("PocketBase", "Raw upload response for $id: $responseBody")
            val response = json.decodeFromString<PocketBasePantryItem>(responseBody)
            
            if (response.image.isNullOrEmpty()) {
                android.util.Log.e("PocketBase", "Upload seemed to succeed but 'image' field is EMPTY in response. Check if 'image' field (Type: File) exists in PocketBase collection.")
            } else {
                android.util.Log.d("PocketBase", "Upload successful for $id. Remote file: ${response.image}")
            }
            response
        } catch (e: Exception) {
            android.util.Log.e("PocketBase", "Failed to upload image for $id", e)
            null
        }
    }

    fun getFileUrl(collectionName: String, recordId: String, fileName: String): String {
        return "$baseUrl/api/files/$collectionName/$recordId/$fileName"
    }

    private var realtimeJob: Job? = null

    fun stopRealtimeSync() {
        android.util.Log.d("PocketBase", "Stopping realtime sync...")
        realtimeJob?.cancel()
        realtimeJob = null
        clientId = null
    }

    fun startRealtimeSync(scope: CoroutineScope) {
        if (realtimeJob != null) return
        
        realtimeJob = scope.launch {
            while (isActive) {
                try {
                    realtimeClient.prepareGet("$baseUrl/api/realtime") {
                        timeout {
                            requestTimeoutMillis = HttpTimeout.INFINITE_TIMEOUT_MS
                            socketTimeoutMillis = HttpTimeout.INFINITE_TIMEOUT_MS
                        }
                    }.execute { response ->
                        val reader = response.bodyAsChannel()
                        while (!reader.isClosedForRead && isActive) {
                            val line = try {
                                reader.readUTF8Line()
                            } catch (e: Exception) {
                                if (e is CancellationException) throw e
                                android.util.Log.w("PocketBase", "Stream read error: ${e.message}")
                                null
                            } ?: break

                            if (line.startsWith("data:")) {
                                val data = line.removePrefix("data:").trim()
                                if (data.isEmpty()) continue
                                
                                try {
                                    val element = json.parseToJsonElement(data).jsonObject
                                    if (element.containsKey("clientId")) {
                                        clientId = element["clientId"]?.jsonPrimitive?.content
                                        android.util.Log.d("PocketBase", "Realtime connected. ClientId: $clientId")
                                        scope.launch { subscribeToCollection("pantry_items") }
                                    } else if (element.containsKey("action")) {
                                        val event = json.decodeFromJsonElement<RealtimeEvent>(element)
                                        scope.launch { _realtimeEvents.emit(event) }
                                    }
                                } catch (e: Exception) {
                                    android.util.Log.e("PocketBase", "Failed to parse realtime data: $data", e)
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    
                    if (e is java.io.IOException) {
                        android.util.Log.w("PocketBase", "Realtime connection lost (${e.message}), retrying in 5s...")
                    } else {
                        android.util.Log.e("PocketBase", "Realtime sync error", e)
                    }
                    delay(5000)
                }
            }
        }
    }

    private suspend fun subscribeToCollection(collection: String) {
        val cid = clientId ?: return
        try {
            client.post("$baseUrl/api/realtime") {
                contentType(ContentType.Application.Json)
                setBody(buildJsonObject {
                    put("clientId", cid)
                    put("subscriptions", buildJsonArray { add(collection) })
                })
            }
            android.util.Log.d("PocketBase", "Subscribed to $collection")
        } catch (e: Exception) {
            android.util.Log.e("PocketBase", "Failed to subscribe", e)
        }
    }
}
