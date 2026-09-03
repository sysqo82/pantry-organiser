package com.pantry.organiser.core.network

import com.pantry.organiser.core.model.BatchPayload
import com.pantry.organiser.core.model.PantryItem
import kotlinx.coroutines.flow.Flow

interface SyncService {
    // Batch Scanning (Ingestion -> Dashboard)
    suspend fun dispatchBatch(payload: BatchPayload)
    fun observeBatches(pantryId: String): Flow<BatchPayload>
    suspend fun fetchBatches(pantryId: String): List<BatchPayload>
    
    // Inventory Items Sync (Dashboard Curator & Ingestion View)
    suspend fun fetchPantryItems(pantryId: String = "default-pantry"): List<PantryItem>
    fun observePantryItems(pantryId: String = "default-pantry"): Flow<PantryItem>
    suspend fun createPantryItem(item: PantryItem): PantryItem?
    suspend fun updatePantryItem(item: PantryItem): PantryItem?
    suspend fun deletePantryItem(itemId: String): Boolean
}
