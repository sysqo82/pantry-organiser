package com.pantry.organiser.dashboard

import android.app.Application
import com.pantry.organiser.dashboard.data.OpenFoodFactsProbeWorker
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class DashboardApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        OpenFoodFactsProbeWorker.schedulePeriodicWork(applicationContext)
    }
}
