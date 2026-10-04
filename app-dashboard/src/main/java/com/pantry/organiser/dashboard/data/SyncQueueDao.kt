package com.pantry.organiser.dashboard.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface SyncQueueDao {
    @Query("SELECT * FROM sync_queue WHERE isProcessed = 0 ORDER BY scannedAt ASC")
    fun getPendingItems(): Flow<List<SyncQueueItem>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertItems(items: List<SyncQueueItem>)

    @Query("UPDATE sync_queue SET isProcessed = 1 WHERE id = :key")
    suspend fun markAsProcessed(key: String)

    @Query("UPDATE sync_queue SET isProcessed = 1 WHERE id = :id OR (itemId != '' AND itemId = :itemId)")
    suspend fun markAsProcessedByIdOrItemId(id: String, itemId: String)

    @Query("UPDATE sync_queue SET isProcessed = 1 WHERE isProcessed = 0")
    suspend fun markAllAsProcessed()

    @Query("DELETE FROM sync_queue WHERE id = :id OR itemId = :itemId")
    suspend fun deleteByIdOrItemId(id: String, itemId: String)

    @Query("DELETE FROM sync_queue")
    suspend fun clearAll()

    @Query("DELETE FROM sync_queue WHERE isProcessed = 1")
    suspend fun clearProcessed()
    
    @Query("SELECT COUNT(*) FROM sync_queue WHERE isProcessed = 0")
    fun getPendingCount(): Flow<Int>
}
