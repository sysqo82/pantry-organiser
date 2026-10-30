package com.pantry.organiser.ingestion.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

object ShoppingWidgetActionKeys {
    val ITEM_ID = ActionParameters.Key<String>("item_id")
    val ITEM_NAME = ActionParameters.Key<String>("item_name")
    val ITEM_BRAND = ActionParameters.Key<String>("item_brand")
    val ITEM_QTY = ActionParameters.Key<String>("item_qty")
    val ITEM_BARCODE = ActionParameters.Key<String>("item_barcode")
}

class ToggleItemExpandAction : ActionCallback {
    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface ToggleEntryPoint {
        fun syncManager(): ShoppingListWidgetSyncManager
    }

    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        val itemId = parameters[ShoppingWidgetActionKeys.ITEM_ID] ?: return
        val entryPoint = EntryPointAccessors.fromApplication(
            context.applicationContext,
            ToggleEntryPoint::class.java
        )
        entryPoint.syncManager().toggleExpandItem(context, itemId)
    }
}

class MarkItemBoughtAction : ActionCallback {
    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface BoughtEntryPoint {
        fun syncManager(): ShoppingListWidgetSyncManager
    }

    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        val itemId = parameters[ShoppingWidgetActionKeys.ITEM_ID] ?: return
        val itemName = parameters[ShoppingWidgetActionKeys.ITEM_NAME] ?: return
        val itemBrand = parameters[ShoppingWidgetActionKeys.ITEM_BRAND]
        val itemQty = parameters[ShoppingWidgetActionKeys.ITEM_QTY]
        val itemBarcode = parameters[ShoppingWidgetActionKeys.ITEM_BARCODE]

        val widgetItem = WidgetShoppingItem(
            id = itemId,
            name = itemName,
            brand = itemBrand,
            packageQuantity = itemQty,
            barcode = itemBarcode
        )

        val entryPoint = EntryPointAccessors.fromApplication(
            context.applicationContext,
            BoughtEntryPoint::class.java
        )
        entryPoint.syncManager().markItemPurchased(context, widgetItem)
    }
}

class DismissItemAction : ActionCallback {
    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface DismissEntryPoint {
        fun syncManager(): ShoppingListWidgetSyncManager
    }

    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        val itemId = parameters[ShoppingWidgetActionKeys.ITEM_ID] ?: return
        val itemName = parameters[ShoppingWidgetActionKeys.ITEM_NAME] ?: "Item"

        val entryPoint = EntryPointAccessors.fromApplication(
            context.applicationContext,
            DismissEntryPoint::class.java
        )
        entryPoint.syncManager().dismissItem(context, itemId, itemName)
    }
}
