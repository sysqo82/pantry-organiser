package com.pantry.organiser.dashboard.data

import androidx.room.*
import com.pantry.organiser.core.model.PastItem
import kotlinx.coroutines.flow.Flow

@Dao
interface PastItemDao {
    @Query("SELECT * FROM past_items ORDER BY updated_at DESC")
    fun getAllPastItems(): Flow<List<PastItem>>

    @Query("SELECT * FROM past_items ORDER BY updated_at DESC")
    suspend fun getAllPastItemsOnce(): List<PastItem>

    @Query("SELECT * FROM past_items WHERE barcode = :barcode LIMIT 1")
    suspend fun getPastItemByBarcode(barcode: String): PastItem?

    @Query("SELECT * FROM past_items WHERE id = :id LIMIT 1")
    suspend fun getPastItemById(id: String): PastItem?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPastItem(item: PastItem)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPastItems(items: List<PastItem>)

    @Delete
    suspend fun deletePastItem(item: PastItem)

    @Query("DELETE FROM past_items WHERE id = :id OR (:id = '' AND barcode = :barcode)")
    suspend fun deleteByIdOrBarcode(id: String, barcode: String? = null)

    @Query("DELETE FROM past_items WHERE barcode = :barcode")
    suspend fun deletePastItemByBarcode(barcode: String)
}
