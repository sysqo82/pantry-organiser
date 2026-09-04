package com.pantry.organiser.dashboard.data

import android.util.Log
import com.pantry.organiser.core.model.BatchPayload
import com.pantry.organiser.core.network.OpenFoodFactsRepository
import com.pantry.organiser.core.network.SyncService
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SyncQueueRepository @Inject constructor(
    private val syncService: SyncService,
    private val offRepository: OpenFoodFactsRepository,
    private val syncQueueDao: SyncQueueDao
) {
    private val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
        android.util.Log.e("SyncQueue", "Unhandled exception in SyncQueue scope: ${throwable.message}")
    }
    private val scope = CoroutineScope(Dispatchers.IO + exceptionHandler)

    private var observeJob: Job? = null

    fun startObserving(pantryId: String) {
        if (observeJob?.isActive == true) return

        // Initial fetch to get missed batches
        scope.launch {
            fetchInitialBatches(pantryId)
        }
        
        // Realtime updates
        observeJob = scope.launch {
            syncService.observeBatches(pantryId).collect { payload ->
                handlePayload(payload)
            }
        }
    }

    fun stopObserving() {
        observeJob?.cancel()
        observeJob = null
    }

    private suspend fun fetchInitialBatches(pantryId: String) {
        try {
            android.util.Log.d("SyncQueue", "Performing initial batch fetch for $pantryId")
            val batches = syncService.fetchBatches(pantryId)
            batches.forEach { handlePayload(it) }
        } catch (e: Exception) {
            android.util.Log.e("SyncQueue", "Initial fetch failed: ${e.message}")
        }
    }

    private suspend fun handlePayload(payload: BatchPayload) = coroutineScope {
        val itemIds = payload.safeItemIds
        val scannedItems = payload.safeItems
        Log.d("SyncQueue", "Processing batch with ${itemIds.size} itemIds and ${scannedItems.size} legacy items")
        val items = mutableListOf<SyncQueueItem>()

        if (itemIds.isNotEmpty()) {
            val fetchedPantryItems = try {
                syncService.fetchPantryItems(payload.pantryId)
            } catch (_: Exception) {
                emptyList()
            }
            val itemMap = fetchedPantryItems.associateBy { it.id }

            itemIds.forEachIndexed { index, itemId ->
                val pantryItem = itemMap[itemId]
                val correspondingScannedItem = scannedItems.getOrNull(index)

                val barcode = pantryItem?.barcode ?: correspondingScannedItem?.barcode ?: ""
                val name = pantryItem?.name?.takeIf { it.isNotBlank() && it != "Unknown Product" }
                    ?: correspondingScannedItem?.productName?.takeIf { it.isNotBlank() && it != "Enriching..." }
                    ?: "Unknown Product"
                val brand = pantryItem?.brand ?: correspondingScannedItem?.brand ?: ""
                val imageUrl = pantryItem?.imageUrl ?: correspondingScannedItem?.imageUrl ?: ""
                val quantity = pantryItem?.packageQuantity ?: correspondingScannedItem?.quantity ?: ""

                items.add(
                    SyncQueueItem(
                        id = "${payload.pantryId}_${payload.timestamp}_${itemId}",
                        itemId = itemId,
                        barcode = barcode,
                        scannedAt = payload.timestamp,
                        batchId = payload.pantryId + payload.timestamp,
                        productName = name,
                        brand = brand,
                        imageUrl = imageUrl,
                        quantity = quantity
                    )
                )
            }
        }
        
        if (items.isEmpty() && scannedItems.isNotEmpty()) {
            val legacyItems = scannedItems.map { scannedItem ->
                async {
                    val needsEnrichment = scannedItem.productName.isBlank() || scannedItem.productName == "Enriching..."
                    val productName: String
                    val brand: String
                    val imageUrl: String
                    val quantity: String

                    if (!needsEnrichment) {
                        productName = scannedItem.productName.ifBlank { "Unknown Product" }
                        brand = scannedItem.brand
                        imageUrl = scannedItem.imageUrl
                        quantity = scannedItem.quantity
                    } else {
                        val offProduct = try {
                            offRepository.getProduct(scannedItem.barcode)
                        } catch (e: Exception) {
                            android.util.Log.e("SyncQueue", "OFF enrichment failed for ${scannedItem.barcode}: ${e.message}")
                            null
                        }
                        productName = offProduct?.displayProductName ?: scannedItem.productName.ifBlank { "Unknown Product" }
                        brand = offProduct?.displayBrands ?: scannedItem.brand
                        imageUrl = offProduct?.imageUrl ?: scannedItem.imageUrl
                        quantity = offProduct?.weight ?: scannedItem.quantity
                    }

                    SyncQueueItem(
                        id = "${payload.pantryId}_${payload.timestamp}_${scannedItem.barcode}_${scannedItem.timestamp}",
                        itemId = "",
                        barcode = scannedItem.barcode,
                        scannedAt = scannedItem.timestamp,
                        batchId = payload.pantryId + payload.timestamp,
                        productName = productName,
                        brand = brand,
                        imageUrl = imageUrl,
                        quantity = quantity
                    )
                }
            }.awaitAll()
            items.addAll(legacyItems)
        }
        
        android.util.Log.d("SyncQueue", "Inserting ${items.size} queue items into local DB")
        syncQueueDao.insertItems(items)
    }

    fun getPendingItems(): Flow<List<SyncQueueItem>> = syncQueueDao.getPendingItems()
    fun getPendingCount(): Flow<Int> = syncQueueDao.getPendingCount()

    suspend fun markAsProcessed(key: String) {
        syncQueueDao.markAsProcessed(key)
    }

    suspend fun clearProcessed() {
        syncQueueDao.clearProcessed()
    }
}
