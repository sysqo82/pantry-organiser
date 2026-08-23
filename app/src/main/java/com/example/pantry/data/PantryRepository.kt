package com.pantry.organiser.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

class PantryRepository(
    private val pantryDao: PantryDao,
    private val filesDir: File? = null,
    private val pocketBaseApi: PocketBaseApi = PocketBaseApi(),
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
) {
    val allItems: Flow<List<PantryItem>> = pantryDao.getAllItems()

    init {
        // One-time Database Sanitization: Clear any legacy bad local URI paths and FIX shelf numbers
        scope.launch {
            val items = pantryDao.getAllItemsOnce()
            items.forEach { item ->
                var updatedItem = item
                var needsUpdate = false

                // 1. Image URI Cleanup
                val uri = item.localImageUri
                if (!uri.isNullOrBlank()) {
                    val isStale = uri.contains("/cache/") || !File(uri).exists()
                    if (isStale) {
                        android.util.Log.i("PantryRepository", "Cleaning up stale local URI for item ${item.id}: $uri")
                        updatedItem = updatedItem.copy(localImageUri = null)
                        needsUpdate = true
                    }
                }

                // 2. Shelf Number Fix (0 -> 1 or out of bounds)
                if (item.shelfNumber !in 1..4) {
                    val fixedShelf = item.shelfNumber.coerceIn(1, 4)
                    android.util.Log.i("PantryRepository", "Fixing shelf number for item ${item.id}: ${item.shelfNumber} -> $fixedShelf")
                    updatedItem = updatedItem.copy(shelfNumber = fixedShelf)
                    needsUpdate = true
                }
                
                // 3. Zone Index Fix (0 -> 1 or out of bounds)
                if (item.zoneIndex !in 1..3) {
                    val fixedZone = item.zoneIndex.coerceIn(1, 3)
                    android.util.Log.i("PantryRepository", "Fixing zone index for item ${item.id}: ${item.zoneIndex} -> $fixedZone")
                    updatedItem = updatedItem.copy(zoneIndex = fixedZone)
                    needsUpdate = true
                }

                // 4. Retroactive Staple Fix: Force items like "Sugar" or "Flour" to BULK_LEVEL
                val nameLower = item.name.lowercase()
                val stapleKeywords = listOf("flour", "sugar", "rice", "pasta", "cereal", "oats", "lentils", "oil")
                if (item.trackingType == TrackingType.DISCRETE_COUNT && stapleKeywords.any { nameLower.contains(it) }) {
                    android.util.Log.i("PantryRepository", "Retroactively fixing tracking type for staple: ${item.name}")
                    updatedItem = updatedItem.copy(trackingType = TrackingType.BULK_LEVEL)
                    needsUpdate = true
                }

                if (needsUpdate) {
                    // Update local and trigger remote sync to fix server
                    updateItem(updatedItem)
                }
            }
        }

        // Start Realtime Subscription
        pocketBaseApi.startRealtimeSync(scope)
        
        // Listen for Realtime Events
        scope.launch {
            pocketBaseApi.realtimeEvents.collect { event ->
                when (event.action) {
                    "create", "update" -> mergeAndInsert(event.record.toLocal())
                    "delete" -> pantryDao.deleteItem(event.record.toLocal())
                }
            }
        }

        // Initial Fetch & Sync
        scope.launch {
            val remoteItems = pocketBaseApi.getItems()
            if (remoteItems.isNotEmpty()) {
                remoteItems.forEach { mergeAndInsert(it.toLocal()) }
            }
        }
    }

    suspend fun addItem(item: PantryItem) {
        // Generate a temporary local ID if none exists
        val itemWithId = if (item.id.isEmpty()) item.copy(id = "local_" + UUID.randomUUID().toString()) else item
        
        // Optimistic local update with range safety
        val safeItem = itemWithId.copy(
            shelfNumber = itemWithId.shelfNumber.coerceIn(1, 4),
            zoneIndex = itemWithId.zoneIndex.coerceIn(1, 3)
        )
        
        android.util.Log.d("PantryRepository", "Adding local item ${safeItem.id}: shelf=${safeItem.shelfNumber}")
        pantryDao.insertItem(safeItem)
        
        // Remote update
        scope.launch {
            android.util.Log.d("PantryRepository", "Creating remote item for local ID: ${safeItem.id}")
            val created = pocketBaseApi.createItem(safeItem.toPocketBase())
            if (created != null) {
                android.util.Log.d("PantryRepository", "Remote item created: ${created.id} for local: ${safeItem.id}")
                // Verify the item still exists locally before replacing it (prevent ghost items)
                val existsLocally = pantryDao.getItemById(safeItem.id) != null
                if (existsLocally) {
                    pantryDao.deleteItem(safeItem)
                    // Use mergeAndInsert to preserve local image path if it was captured while sync was pending
                    mergeAndInsert(created.toLocal())
                }
            } else {
                android.util.Log.e("PantryRepository", "Failed to create remote item for ${safeItem.id}")
            }
        }
    }

    suspend fun updateItem(item: PantryItem) {
        val safeItem = item.copy(
            shelfNumber = item.shelfNumber.coerceIn(1, 4),
            zoneIndex = item.zoneIndex.coerceIn(1, 3)
        )
        android.util.Log.d("PantryRepository", "Updating DB item ${safeItem.id}: shelf=${safeItem.shelfNumber}")
        pantryDao.updateItem(safeItem)
        
        if (!safeItem.id.startsWith("local_")) {
            scope.launch {
                pocketBaseApi.updateItem(safeItem.id, safeItem.toPocketBase())
            }
        }
    }

    suspend fun updateLocalImageUri(item: PantryItem, tempPath: String) {
        val tempFile = File(tempPath)
        if (!tempFile.exists()) {
            android.util.Log.e("PantryRepository", "Update image failed: Temp file not found at $tempPath")
            return
        }

        val persistentFile = moveFileToPersistentStorage(tempPath)
        val persistentPath = persistentFile.absolutePath
        android.util.Log.d("PantryRepository", "Moved photo to persistent storage: $persistentPath")
        
        val updatedItem = item.copy(
            localImageUri = persistentPath,
            updatedAt = System.currentTimeMillis()
        )
        
        pantryDao.updateItem(updatedItem)
        
        // Sync to PocketBase
        if (!item.id.startsWith("local_")) {
            scope.launch {
                android.util.Log.d("PantryRepository", "Uploading photo for ${item.id} from $persistentPath")
                val response = pocketBaseApi.uploadImage(item.id, persistentFile)
                if (response != null) {
                    android.util.Log.d("PantryRepository", "Photo upload success for ${item.id}. Remote image field: ${response.image}")
                    // Update local record with the new remote URL (constructed in toLocal())
                    // and KEEP the persistent local path.
                    val remoteItem = response.toLocal()
                    pantryDao.updateItem(remoteItem.copy(localImageUri = persistentPath))
                } else {
                    android.util.Log.e("PantryRepository", "Photo upload FAILED for ${item.id}")
                }
            }
        }
    }

    private fun moveFileToPersistentStorage(tempPath: String): File {
        val tempFile = File(tempPath)
        val imageDir = File(filesDir, "images").apply { mkdirs() }
        val persistentFile = File(imageDir, "product_${System.currentTimeMillis()}.jpg")
        tempFile.copyTo(persistentFile, overwrite = true)
        try { tempFile.delete() } catch (_: Exception) {}
        return persistentFile
    }

    suspend fun deleteItem(item: PantryItem) {
        pantryDao.deleteItem(item)
        
        if (!item.id.startsWith("local_")) {
            scope.launch {
                pocketBaseApi.deleteItem(item.id)
            }
        }
    }

    suspend fun getItemByBarcode(barcode: String): PantryItem? {
        return pantryDao.getItemByBarcode(barcode)
    }

    private suspend fun mergeAndInsert(remoteItem: PantryItem) {
        val existing = pantryDao.getItemById(remoteItem.id)
        
        // Hard-enforce staple tracking type on inbound data
        var finalItem = remoteItem
        val nameLower = finalItem.name.lowercase()
        val stapleKeywords = listOf("flour", "sugar", "rice", "pasta", "cereal", "oats", "lentils", "oil", "salt", "syrup", "honey")
        if (finalItem.trackingType == TrackingType.DISCRETE_COUNT && stapleKeywords.any { nameLower.contains(it) }) {
            finalItem = finalItem.copy(trackingType = TrackingType.BULK_LEVEL)
            android.util.Log.i("PantryRepository", "Inbound update for ${finalItem.name} corrected to Staple mode.")
        }
        
        // CONTENT-BASED COMPARISON: Only skip if core fields are identical.
        if (existing != null && 
            existing.name == finalItem.name &&
            existing.brand == finalItem.brand &&
            existing.barcode == finalItem.barcode &&
            existing.sealedCount == finalItem.sealedCount &&
            existing.activeFill == finalItem.activeFill &&
            existing.shelfNumber == finalItem.shelfNumber &&
            existing.zoneIndex == finalItem.zoneIndex &&
            existing.trackingType == finalItem.trackingType &&
            existing.imageUrl == finalItem.imageUrl
        ) {
            return
        }

        // Sanitize local path logic
        val sanitizedUri = existing?.localImageUri?.takeIf { it.isNotBlank() && !it.contains("/cache/") && File(it).exists() }

        android.util.Log.d("PantryRepository", "Syncing remote update for ${finalItem.id}. Mode: ${finalItem.trackingType}, Shelf: ${finalItem.shelfNumber}")
        pantryDao.insertItem(finalItem.copy(localImageUri = sanitizedUri))
    }
}
