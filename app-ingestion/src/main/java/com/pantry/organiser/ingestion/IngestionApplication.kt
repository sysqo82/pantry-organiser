package com.pantry.organiser.ingestion

import android.app.Application
import android.util.Log
import com.pantry.organiser.core.network.SyncService
import com.pantry.organiser.ingestion.widget.ShoppingListSyncWorker
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class IngestionApplication : Application() {

    @Inject
    lateinit var syncService: SyncService

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        ShoppingListSyncWorker.schedulePeriodicWork(applicationContext)
        ShoppingListSyncWorker.enqueueImmediateWork(applicationContext)

        // Observe real-time past_items events and update widget automatically when items are added/removed
        applicationScope.launch {
            try {
                syncService.observePastItems("default-pantry").collect {
                    Log.d("IngestionApp", "Realtime past_item change received (${it.name}). Updating shopping list widget!")
                    ShoppingListSyncWorker.enqueueImmediateWork(applicationContext)
                }
            } catch (e: Exception) {
                Log.e("IngestionApp", "Error observing past_items realtime stream: ${e.message}")
            }
        }
    }
}
