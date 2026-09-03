package com.pantry.organiser.data

import com.pantry.organiser.core.model.PantryItem
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface PantryDao {
    @Query("SELECT * FROM pantry_items")
    fun getAllItems(): Flow<List<PantryItem>>

    @Query("SELECT * FROM pantry_items")
    suspend fun getAllItemsOnce(): List<PantryItem>

    @Query("SELECT * FROM pantry_items WHERE barcode = :barcode LIMIT 1")
    suspend fun getItemByBarcode(barcode: String): PantryItem?

    @Query("SELECT * FROM pantry_items WHERE id = :id LIMIT 1")
    suspend fun getItemById(id: String): PantryItem?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItem(item: PantryItem)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItems(items: List<PantryItem>)

    @Update
    suspend fun updateItem(item: PantryItem)

    @Delete
    suspend fun deleteItem(item: PantryItem)

    @Query("DELETE FROM pantry_items WHERE id = :id OR (barcode IS NOT NULL AND barcode = :barcode)")
    suspend fun deleteByIdOrBarcode(id: String, barcode: String? = null)

    @Query("DELETE FROM pantry_items WHERE id NOT IN (:validIds) AND id NOT LIKE 'local_%'")
    suspend fun deleteItemsNotIn(validIds: List<String>)

    @Query("DELETE FROM pantry_items WHERE id NOT LIKE 'local_%'")
    suspend fun deleteAllServerItems()
}
