package com.pantry.organiser.core.network

import android.util.Log
import com.pantry.organiser.core.model.BatchPayload
import com.pantry.organiser.core.model.PantryItem
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.utils.io.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.*

class PocketBaseSyncService(
    private val client: HttpClient,
    private val baseUrl: String
) : SyncService {
    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        encodeDefaults = true
    }

    override suspend fun dispatchBatch(payload: BatchPayload) {
        val url = "$baseUrl/api/collections/batch_payloads/records"
        try {
            Log.d("PocketBaseSync", "Dispatching batch to: $url (Items: ${payload.safeItems.size})")
            val response = client.post(url) {
                contentType(ContentType.Application.Json)
                setBody(payload)
                timeout {
                    requestTimeoutMillis = 30000 // 30s for dispatch
                }
            }
            android.util.Log.d("PocketBaseSync", "Batch dispatched successfully. Status: ${response.status}")
            if (!response.status.isSuccess()) {
                val errorBody = try { response.bodyAsText() } catch (e: Exception) { "No error body" }
                android.util.Log.e("PocketBaseSync", "Server error (${response.status}): $errorBody")
                throw Exception("Server returned ${response.status}: $errorBody")
            }
        } catch (e: Exception) {
            android.util.Log.e("PocketBaseSync", "Failed to dispatch batch to $url: ${e.message}")
            throw e
        }
    }

    private suspend fun subscribe(clientId: String, collection: String) {
        try {
            val response = client.post("$baseUrl/api/realtime") {
                contentType(ContentType.Application.Json)
                setBody(RealtimeSubscribeRequest(clientId, listOf(collection)))
            }
            if (response.status.isSuccess()) {
                android.util.Log.d("PocketBaseSync", "Successfully subscribed to $collection")
            } else {
                android.util.Log.e("PocketBaseSync", "Failed to subscribe to $collection: ${response.status}")
            }
        } catch (e: Exception) {
            android.util.Log.e("PocketBaseSync", "Exception during subscription to $collection: ${e.message}")
        }
    }

    override suspend fun fetchBatches(pantryId: String): List<BatchPayload> {
        val url = "$baseUrl/api/collections/batch_payloads/records"
        return try {
            val response = client.get(url) {
                parameter("filter", "pantryId='$pantryId'")
                parameter("sort", "-created")
                parameter("perPage", 20)
            }
            if (!response.status.isSuccess()) {
                val errorBody = try { response.bodyAsText() } catch (_: Exception) { "" }
                android.util.Log.e("PocketBaseSync", "Failed to fetch batches (${response.status}): $errorBody")
                return emptyList()
            }
            val pbList: PocketBaseListResponse<BatchPayload> = response.body()
            pbList.items
        } catch (e: Exception) {
            android.util.Log.e("PocketBaseSync", "Failed to fetch batches: ${e.message}", e)
            emptyList()
        }
    }

    override fun observeBatches(pantryId: String): Flow<BatchPayload> = flow {
        var retryDelay = 5000L
        val maxDelay = 60000L

        while (currentCoroutineContext().isActive) {
            try {
                val url = "$baseUrl/api/realtime"
                android.util.Log.d("PocketBaseSync", "Connecting to realtime: $url for $pantryId")
                client.prepareGet(url) {
                    timeout {
                        requestTimeoutMillis = HttpTimeout.INFINITE_TIMEOUT_MS
                        connectTimeoutMillis = 15000
                        socketTimeoutMillis = HttpTimeout.INFINITE_TIMEOUT_MS
                    }
                }.execute { response ->
                    retryDelay = 5000L
                    android.util.Log.d("PocketBaseSync", "Realtime stream opened for $pantryId. Status: ${response.status}")
                    val channel = response.bodyAsChannel()
                    while (!channel.isClosedForRead && currentCoroutineContext().isActive) {
                        val line = try {
                            channel.readUTF8Line()
                        } catch (e: Exception) {
                            if (e is kotlinx.coroutines.CancellationException) throw e
                            android.util.Log.w("PocketBaseSync", "Error reading line from realtime for $pantryId: ${e.message}")
                            null
                        } ?: break

                        if (line.startsWith("data:")) {
                            val data = line.removePrefix("data:").trim()
                            if (data.isNotEmpty()) {
                                try {
                                    val element = json.parseToJsonElement(data)
                                    if (element is JsonObject && element.containsKey("clientId")) {
                                        val clientId = element["clientId"]?.jsonPrimitive?.content
                                        if (clientId != null) {
                                            android.util.Log.d("PocketBaseSync", "Received clientId: $clientId. Subscribing...")
                                            coroutineScope {
                                                launch { subscribe(clientId, "batch_payloads") }
                                            }
                                        }
                                    } else {
                                        val event = json.decodeFromJsonElement<RealtimeEvent>(element)
                                        if (event.action == "create" || event.action == "update") {
                                            val payload = json.decodeFromJsonElement<BatchPayload>(event.record)
                                            if (payload.pantryId == pantryId) {
                                                emit(payload)
                                            }
                                        }
                                    }
                                } catch (e: Exception) {
                                    android.util.Log.w("PocketBaseSync", "Failed to parse realtime data from $pantryId: $data. Error: ${e.message}")
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException || !currentCoroutineContext().isActive) throw e
                val isNetworkDown = e is java.net.UnknownHostException || 
                                    e is java.net.ConnectException || 
                                    e is java.net.SocketException ||
                                    e.cause is java.net.UnknownHostException
                if (isNetworkDown) {
                    android.util.Log.w("PocketBaseSync", "Host unreachable (${e.message}). Retrying in ${retryDelay / 1000}s...")
                } else {
                    android.util.Log.e("PocketBaseSync", "Connection error for $pantryId: ${e.message}. Retrying in ${retryDelay / 1000}s...")
                }
                if (!currentCoroutineContext().isActive) break
                delay(retryDelay)
                retryDelay = (retryDelay * 2).coerceAtMost(maxDelay)
            } catch (t: Throwable) {
                if (!currentCoroutineContext().isActive) throw t
                android.util.Log.e("PocketBaseSync", "Unexpected fatal error in realtime observer for $pantryId", t)
                if (!currentCoroutineContext().isActive) break
                delay(10000)
            }
        }
    }

    override suspend fun fetchPantryItems(pantryId: String): List<PantryItem> {
        val url = "$baseUrl/api/collections/pantry_items/records"
        return try {
            val response = client.get(url) {
                parameter("perPage", 500)
            }
            if (!response.status.isSuccess()) {
                val errorBody = try { response.bodyAsText() } catch (_: Exception) { "" }
                android.util.Log.e("PocketBaseSync", "Failed to fetch pantry_items (${response.status}): $errorBody")
                throw Exception("HTTP ${response.status}: $errorBody")
            }
            val pbList: PocketBaseListResponse<PocketBasePantryItem> = response.body()
            pbList.items.map { it.toLocal() }
        } catch (e: Exception) {
            android.util.Log.e("PocketBaseSync", "Failed to fetch pantry_items: ${e.message}", e)
            throw e
        }
    }

    private val writeMutex = Mutex()

    override suspend fun createPantryItem(item: PantryItem): PantryItem? = writeMutex.withLock {
        createPantryItemInternal(item)
    }

    private suspend fun createPantryItemInternal(item: PantryItem): PantryItem? {
        val url = "$baseUrl/api/collections/pantry_items/records"
        val pbItem = item.toPocketBase()
        val jsonBody = json.encodeToString(PocketBasePantryItem.serializer(), pbItem)
        var lastException: Exception? = null

        for (attempt in 1..3) {
            try {
                Log.d("PocketBaseSync", "Creating pantry_item (Attempt $attempt/3) with body: $jsonBody")
                val httpResponse = client.post(url) {
                    contentType(ContentType.Application.Json)
                    setBody(jsonBody)
                }
                val responseText = httpResponse.bodyAsText()
                Log.d("PocketBaseSync", "Create response status ${httpResponse.status}: $responseText")

                if (httpResponse.status.isSuccess()) {
                    val response: PocketBasePantryItem = json.decodeFromString(responseText)
                    val createdLocal = response.toLocal()
                    delay(250) // 250ms spacing between writes to prevent PocketBase SQLite write lock collisions
                    return createdLocal
                } else {
                    Log.e("PocketBaseSync", "Create attempt $attempt failed (${httpResponse.status}): $responseText")
                }
            } catch (e: Exception) {
                lastException = e
                val errorBody = if (e is ResponseException) {
                    try { e.response.bodyAsText() } catch (_: Exception) { "" }
                } else e.message ?: ""
                Log.e("PocketBaseSync", "Attempt $attempt exception for create ${item.name}: $errorBody", e)
                if (attempt < 3) {
                    delay(300L * attempt) // 300ms, 600ms backoff
                }
            }
        }
        android.util.Log.e("PocketBaseSync", "Failed all 3 attempts to create pantry_item: ${item.name}", lastException)
        return null
    }

    override suspend fun updatePantryItem(item: PantryItem): PantryItem? = writeMutex.withLock {
        if (item.id.isEmpty() || item.id.startsWith("local_")) {
            return@withLock createPantryItemInternal(item)
        }
        val url = "$baseUrl/api/collections/pantry_items/records/${item.id}"
        val pbItem = item.toPocketBase()
        val jsonBody = json.encodeToString(PocketBasePantryItem.serializer(), pbItem)
        var lastException: Exception? = null

        for (attempt in 1..3) {
            try {
                Log.d("PocketBaseSync", "Updating pantry_item ${item.id} (Attempt $attempt/3) with body: $jsonBody")
                val httpResponse = client.patch(url) {
                    contentType(ContentType.Application.Json)
                    setBody(jsonBody)
                }
                val responseText = httpResponse.bodyAsText()
                if (httpResponse.status.isSuccess()) {
                    val response: PocketBasePantryItem = json.decodeFromString(responseText)
                    val updatedLocal = response.toLocal()
                    delay(250) // 250ms spacing between writes
                    return updatedLocal
                } else {
                    Log.e("PocketBaseSync", "Update attempt $attempt failed (${httpResponse.status}): $responseText")
                }
            } catch (e: Exception) {
                lastException = e
                val errorBody = if (e is ResponseException) {
                    try { e.response.bodyAsText() } catch (_: Exception) { "" }
                } else e.message ?: ""
                Log.e("PocketBaseSync", "Attempt $attempt exception for update ${item.id}: $errorBody", e)
                if (attempt < 3) {
                    delay(300L * attempt)
                }
            }
        }
        android.util.Log.e("PocketBaseSync", "Failed all 3 attempts to update pantry_item ${item.id}", lastException)
        return null
    }

    override suspend fun deletePantryItem(itemId: String): Boolean = writeMutex.withLock {
        if (itemId.isEmpty() || itemId.startsWith("local_")) return true
        val url = "$baseUrl/api/collections/pantry_items/records/$itemId"
        for (attempt in 1..3) {
            try {
                val success = client.delete(url).status.isSuccess()
                if (success) {
                    delay(250)
                    return true
                }
            } catch (e: Exception) {
                if (attempt < 3) delay(300L * attempt)
            }
        }
        return false
    }

    override fun observePantryItems(pantryId: String): Flow<PantryItem> = flow {
        var retryDelay = 5000L
        val maxDelay = 60000L

        while (currentCoroutineContext().isActive) {
            try {
                val url = "$baseUrl/api/realtime"
                android.util.Log.d("PocketBaseSync", "Connecting to realtime pantry_items: $url")
                client.prepareGet(url) {
                    timeout {
                        requestTimeoutMillis = HttpTimeout.INFINITE_TIMEOUT_MS
                        connectTimeoutMillis = 15000
                        socketTimeoutMillis = HttpTimeout.INFINITE_TIMEOUT_MS
                    }
                }.execute { response ->
                    retryDelay = 5000L
                    val channel = response.bodyAsChannel()
                    while (!channel.isClosedForRead && currentCoroutineContext().isActive) {
                        val line = try {
                            channel.readUTF8Line()
                        } catch (e: Exception) {
                            if (e is kotlinx.coroutines.CancellationException) throw e
                            null
                        } ?: break

                        if (line.startsWith("data:")) {
                            val data = line.removePrefix("data:").trim()
                            if (data.isNotEmpty()) {
                                try {
                                    val element = json.parseToJsonElement(data)
                                    if (element is JsonObject && element.containsKey("clientId")) {
                                        val clientId = element["clientId"]?.jsonPrimitive?.content
                                        if (clientId != null) {
                                            coroutineScope {
                                                launch { subscribe(clientId, "pantry_items") }
                                            }
                                        }
                                    } else {
                                        val event = json.decodeFromJsonElement<RealtimeEvent>(element)
                                        if (event.action == "create" || event.action == "update") {
                                            val pbItem = json.decodeFromJsonElement<PocketBasePantryItem>(event.record)
                                            emit(pbItem.toLocal())
                                        } else if (event.action == "delete") {
                                            val pbItem = json.decodeFromJsonElement<PocketBasePantryItem>(event.record)
                                            val deletedItem = pbItem.toLocal().copy(sealedCount = -1)
                                            emit(deletedItem)
                                        }
                                    }
                                } catch (e: Exception) {
                                    android.util.Log.w("PocketBaseSync", "Failed to parse realtime pantry_items data: $data", e)
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException || !currentCoroutineContext().isActive) throw e
                val isNetworkDown = e is java.net.UnknownHostException || 
                                    e is java.net.ConnectException || 
                                    e is java.net.SocketException ||
                                    e.cause is java.net.UnknownHostException
                if (isNetworkDown) {
                    android.util.Log.w("PocketBaseSync", "Host unreachable (${e.message}). Retrying in ${retryDelay / 1000}s...")
                } else {
                    android.util.Log.e("PocketBaseSync", "Connection or stream error in observePantryItems: ${e.message}. Retrying in ${retryDelay / 1000}s...")
                }
                if (!currentCoroutineContext().isActive) break
                delay(retryDelay)
                retryDelay = (retryDelay * 2).coerceAtMost(maxDelay)
            } catch (t: Throwable) {
                if (!currentCoroutineContext().isActive) throw t
                android.util.Log.e("PocketBaseSync", "Fatal error in observePantryItems", t)
                if (!currentCoroutineContext().isActive) break
                delay(10000)
            }
        }
    }
}
