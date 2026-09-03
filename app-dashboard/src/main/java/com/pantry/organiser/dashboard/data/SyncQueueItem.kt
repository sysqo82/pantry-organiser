package com.pantry.organiser.dashboard.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sync_queue")
data class SyncQueueItem(
    @PrimaryKey val id: String,
    val itemId: String = "",
    val barcode: String = "",
    val scannedAt: Long = System.currentTimeMillis(),
    val batchId: String = "",
    val isProcessed: Boolean = false,
    val productName: String? = "",
    val brand: String? = "",
    val imageUrl: String? = "",
    val quantity: String? = ""
)
