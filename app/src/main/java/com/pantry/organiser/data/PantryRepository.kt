package com.pantry.organiser.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

class PantryRepository @JvmOverloads constructor(
    private val pantryDao: PantryDao,
    private val pocketBaseApi: PocketBaseApi = PocketBaseApi(),
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    private val filesDir: File? = null
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
                
                // Multipack Heuristic Fix removed here - handled by ViewModel Sanity Check


                // 6. API Image Fix-up: Move OFF links from old imageUrl field to apiImageUrl if missing

                val currentImageUrl = item.imageUrl ?: ""
                if (item.apiImageUrl == null && currentImageUrl.contains("openfoodfacts.org")) {
                    android.util.Log.i("PantryRepository", "Fixing API image field for: ${item.name}")
                    updatedItem = updatedItem.copy(apiImageUrl = currentImageUrl, imageUrl = null)
                    needsUpdate = true
                }

                if (needsUpdate) {
                    // Update local and trigger remote sync to fix server
                    updateItem(updatedItem)
                }
            }
        }

        // Start Realtime Subscription
        startRealtimeSync()
        
        // Listen for Realtime Events
        scope.launch {
            pocketBaseApi.realtimeEvents.collect { event ->
                when (event.action) {
                    "create", "update" -> mergeAndInsert(event.record.toLocal())
                    "delete" -> pantryDao.deleteItem(event.record.toLocal())
                }
            }
        }

        // Initial Fetch & Reconciliation Sync
        scope.launch {
            val remotePocketItems = pocketBaseApi.getItems()
            if (remotePocketItems != null) {
                val remoteItems = remotePocketItems.map { it.toLocal() }
                val remoteIds = remoteItems.map { it.id }.toSet()
                
                // Get all local items to identify and remove "ghost" items
                val localItems = pantryDao.getAllItemsOnce()
                localItems.forEach { local ->
                    // A "ghost" is a server-indexed item that no longer exists on the server.
                    // We preserve items starting with "local_" as they are pending upload.
                    if (!local.id.startsWith("local_") && !remoteIds.contains(local.id)) {
                        android.util.Log.i("PantryRepository", "Pruning ghost item: ${local.name} (${local.id})")
                        pantryDao.deleteItem(local)
                    }
                }
                
                // Merge/Update with current server state
                remoteItems.forEach { mergeAndInsert(it) }
            } else {
                android.util.Log.w("PantryRepository", "Reconciliation skipped: Server unreachable.")
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

    fun startRealtimeSync() {
        pocketBaseApi.startRealtimeSync(scope)
    }

    fun stopRealtimeSync() {
        pocketBaseApi.stopRealtimeSync()
    }

    private suspend fun mergeAndInsert(remoteItem: PantryItem) {
        val existing = pantryDao.getItemById(remoteItem.id)
        
        // Use inbound data as-is. Classification logic moved to ViewModel/SanityCheck.
        val finalItem = remoteItem

        
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
            existing.imageUrl == finalItem.imageUrl &&
            existing.apiImageUrl == finalItem.apiImageUrl &&
            existing.unitsPerPack == finalItem.unitsPerPack &&
            existing.activeCount == finalItem.activeCount
        ) {
            return
        }


        // PHOTO SYNC LOGIC: If the remote URL has changed, it means a new photo was uploaded.
        // We compare the full URL which includes the 'v' (version) timestamp from PocketBase.
        val hasPhotoChanged = existing != null && existing.imageUrl != finalItem.imageUrl
        
        if (hasPhotoChanged) {
            android.util.Log.i("PantryRepository", "Photo update detected for ${finalItem.name}. Remote: ${finalItem.imageUrl}, Local: ${existing?.imageUrl}. Discarding local cache.")
        }

        val sanitizedUri = if (hasPhotoChanged) {
            null
        } else {
            existing?.localImageUri?.takeIf { it.isNotBlank() && !it.contains("/cache/") && File(it).exists() }
        }

        android.util.Log.d("PantryRepository", "Syncing update for ${finalItem.name} (${finalItem.id}): API_IMG=${finalItem.apiImageUrl}, CUSTOM_IMG=${finalItem.imageUrl}")
        pantryDao.insertItem(finalItem.copy(localImageUri = sanitizedUri))
    }
}
