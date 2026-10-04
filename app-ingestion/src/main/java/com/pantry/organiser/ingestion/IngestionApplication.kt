package com.pantry.organiser.ingestion

import android.app.Application
import com.pantry.organiser.core.network.SyncService
import com.pantry.organiser.ingestion.widget.ShoppingListSyncWorker
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class IngestionApplication : Application() {

    @Inject
    lateinit var syncService: SyncService

    override fun onCreate() {
        super.onCreate()
        ShoppingListSyncWorker.schedulePeriodicWork(applicationContext)
        ShoppingListSyncWorker.enqueueImmediateWork(applicationContext)

    }
}
