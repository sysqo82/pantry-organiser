package com.pantry.organiser.dashboard.ui

import com.pantry.organiser.core.model.PantryItem
import com.pantry.organiser.dashboard.data.SyncQueueItem

sealed class OverlayContext {
    data class SyncQueueEnrichment(
        val syncItem: SyncQueueItem,
        val existingItem: PantryItem? = null
    ) : OverlayContext()
    
    data class ManualEntry(val shelf: Int, val zone: Int) : OverlayContext()
    data class ItemDetail(val item: PantryItem) : OverlayContext()
    data class ItemEdit(val item: PantryItem) : OverlayContext()
}

// In a real app, this would be a more complex state management
// but we'll start with a simple interface concept.
