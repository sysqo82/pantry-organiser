package com.pantry.organiser.ingestion.widget

import android.content.Context
import android.util.Log
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import com.pantry.organiser.core.model.BatchPayload
import com.pantry.organiser.core.model.PastItem
import com.pantry.organiser.core.model.ScannedItem
import com.pantry.organiser.core.network.SyncService
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ShoppingListWidgetSyncManager @Inject constructor(
    private val syncService: SyncService
) {

    suspend fun syncWidget(context: Context, pantryId: String = "default-pantry"): Result<List<WidgetShoppingItem>> {
        return try {
            val pastItems = syncService.fetchPastItems(pantryId)
            val widgetItems = processPastItemsForWidget(pastItems)
            val newState = ShoppingWidgetState.Success(
                items = widgetItems,
                lastUpdatedMillis = System.currentTimeMillis()
            )

            updateWidgetState(context, newState)
            Result.success(widgetItems)
        } catch (e: Exception) {
            Log.e("ShoppingWidgetSync", "Error updating widget from past_items: ${e.message}", e)
            val errorState = ShoppingWidgetState.Error(
                message = e.localizedMessage ?: "Failed to update widget"
            )
            updateWidgetState(context, errorState)
            Result.failure(e)
        }
    }

    suspend fun markItemPurchased(
        context: Context,
        item: WidgetShoppingItem,
        pantryId: String = "default-pantry"
    ): Boolean {
        return try {
            // 1. Dispatch BatchPayload with ScannedItem to shopping list intake (batch_payloads)
            val scannedItem = ScannedItem(
                barcode = item.barcode ?: "",
                productName = item.name,
                brand = item.brand ?: "",
                quantity = item.packageQuantity ?: "",
                timestamp = System.currentTimeMillis()
            )

            val payload = BatchPayload(
                pantryId = pantryId,
                itemIds = emptyList(),
                items = listOf(scannedItem),
                timestamp = System.currentTimeMillis()
            )

            syncService.dispatchBatch(payload)

            // 2. Remove item from past_items
            syncService.deletePastItem(item.id)

            // 3. Re-fetch and update widget state with feedback
            val updatedPastItems = syncService.fetchPastItems(pantryId)
            val updatedWidgetItems = processPastItemsForWidget(updatedPastItems)

            val newState = ShoppingWidgetState.Success(
                items = updatedWidgetItems,
                expandedItemId = null,
                statusMessage = "Sent ${item.name} to Intake",
                lastUpdatedMillis = System.currentTimeMillis()
            )

            updateWidgetState(context, newState)
            true
        } catch (e: Exception) {
            Log.e("ShoppingWidgetSync", "Error marking item purchased: ${e.message}", e)
            false
        }
    }

    suspend fun dismissItem(
        context: Context,
        itemId: String,
        itemName: String,
        pantryId: String = "default-pantry"
    ): Boolean {
        return try {
            // Remove item directly from past_items DB
            syncService.deletePastItem(itemId)

            // Re-fetch and update widget state
            val updatedPastItems = syncService.fetchPastItems(pantryId)
            val updatedWidgetItems = processPastItemsForWidget(updatedPastItems)

            val newState = ShoppingWidgetState.Success(
                items = updatedWidgetItems,
                expandedItemId = null,
                statusMessage = "Removed $itemName",
                lastUpdatedMillis = System.currentTimeMillis()
            )

            updateWidgetState(context, newState)
            true
        } catch (e: Exception) {
            Log.e("ShoppingWidgetSync", "Error dismissing item: ${e.message}", e)
            false
        }
    }

    suspend fun toggleExpandItem(context: Context, itemId: String) {
        val manager = GlanceAppWidgetManager(context)
        val glanceIds = manager.getGlanceIds(ShoppingListWidget::class.java)

        for (glanceId in glanceIds) {
            updateAppWidgetState(
                context = context,
                definition = ShoppingWidgetStateDefinition,
                glanceId = glanceId
            ) { currentState ->
                if (currentState is ShoppingWidgetState.Success) {
                    val nextExpanded = if (currentState.expandedItemId == itemId) null else itemId
                    currentState.copy(expandedItemId = nextExpanded)
                } else {
                    currentState
                }
            }
            ShoppingListWidget().update(context, glanceId)
        }
    }

    suspend fun updateWidgetState(context: Context, newState: ShoppingWidgetState) {
        val manager = GlanceAppWidgetManager(context)
        val glanceIds = manager.getGlanceIds(ShoppingListWidget::class.java)

        for (glanceId in glanceIds) {
            updateAppWidgetState(
                context = context,
                definition = ShoppingWidgetStateDefinition,
                glanceId = glanceId
            ) {
                newState
            }
            ShoppingListWidget().update(context, glanceId)
        }
    }

    companion object {
        fun processPastItemsForWidget(items: List<PastItem>): List<WidgetShoppingItem> {
            return items
                .distinctBy { item ->
                    item.barcode?.takeIf { it.isNotBlank() } ?: item.name.trim().lowercase()
                }
                .sortedBy { it.name.lowercase() }
                .map {
                    WidgetShoppingItem(
                        id = it.id,
                        name = it.name,
                        brand = it.brand,
                        packageQuantity = it.packageQuantity,
                        barcode = it.barcode
                    )
                }
        }
    }
}
