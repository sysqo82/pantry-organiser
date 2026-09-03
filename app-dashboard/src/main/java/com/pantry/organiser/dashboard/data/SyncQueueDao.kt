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

    @Query("DELETE FROM sync_queue WHERE isProcessed = 1")
    suspend fun clearProcessed()
    
    @Query("SELECT COUNT(*) FROM sync_queue WHERE isProcessed = 0")
    fun getPendingCount(): Flow<Int>
}
