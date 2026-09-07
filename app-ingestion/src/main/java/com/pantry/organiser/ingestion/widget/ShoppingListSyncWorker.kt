package com.pantry.organiser.ingestion.widget

import android.content.Context
import android.util.Log
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.pantry.organiser.core.model.PastItem
import com.pantry.organiser.core.network.SyncService
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.concurrent.TimeUnit

@Serializable
data class WidgetShoppingItem(
    val id: String,
    val name: String,
    val brand: String? = null,
    val packageQuantity: String? = null,
    val barcode: String? = null
)

class ShoppingListSyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface SyncWorkerEntryPoint {
        fun syncService(): SyncService
    }

    override suspend fun doWork(): Result {
        return try {
            val entryPoint = EntryPointAccessors.fromApplication(
                applicationContext,
                SyncWorkerEntryPoint::class.java
            )
            val syncService = entryPoint.syncService()
            val pastItems = syncService.fetchPastItems("default-pantry")

            val widgetItems = processPastItemsForWidget(pastItems)
            val jsonString = json.encodeToString(widgetItems)

            val manager = GlanceAppWidgetManager(applicationContext)
            val glanceIds = manager.getGlanceIds(ShoppingListWidget::class.java)

            for (glanceId in glanceIds) {
                updateAppWidgetState(
                    context = applicationContext,
                    definition = PreferencesGlanceStateDefinition,
                    glanceId = glanceId
                ) { prefs ->
                    prefs.toMutablePreferences().apply {
                        this[KEY_SHOPPING_ITEMS] = jsonString
                    }
                }
                ShoppingListWidget().update(applicationContext, glanceId)
            }

            Result.success()
        } catch (e: Exception) {
            Log.e("ShoppingListWorker", "Error updating widget from past_items: ${e.message}", e)
            Result.retry()
        }
    }

    companion object {
        const val PERIODIC_WORK_NAME = "shopping_list_widget_sync_periodic"
        const val IMMEDIATE_WORK_NAME = "shopping_list_widget_sync_immediate"
        val KEY_SHOPPING_ITEMS = stringPreferencesKey("shopping_items_json")

        private val json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

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

        fun schedulePeriodicWork(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val workRequest = PeriodicWorkRequestBuilder<ShoppingListSyncWorker>(30, TimeUnit.MINUTES)
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                PERIODIC_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                workRequest
            )
        }

        fun enqueueImmediateWork(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val workRequest = OneTimeWorkRequestBuilder<ShoppingListSyncWorker>()
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                IMMEDIATE_WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                workRequest
            )
        }
    }
}
