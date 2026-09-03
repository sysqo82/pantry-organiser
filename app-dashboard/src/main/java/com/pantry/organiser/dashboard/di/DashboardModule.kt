package com.pantry.organiser.dashboard.di

import android.content.Context
import com.pantry.organiser.core.model.PantryConstants
import com.pantry.organiser.core.network.OpenFoodFactsRepository
import com.pantry.organiser.core.network.PocketBaseSyncService
import com.pantry.organiser.core.network.SyncService
import com.pantry.organiser.dashboard.data.PantryDao
import com.pantry.organiser.dashboard.data.PantryDatabase
import com.pantry.organiser.dashboard.data.SyncQueueDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.ktor.client.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DashboardModule {

    @Provides
    @Singleton
    fun provideHttpClient(): HttpClient = HttpClient(OkHttp) {
        engine {
            config {
                val dispatcher = okhttp3.Dispatcher().apply {
                    maxRequests = 64
                    maxRequestsPerHost = 20
                }
                dispatcher(dispatcher)
            }
        }
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                explicitNulls = false
            })
        }
        install(HttpTimeout) {
            requestTimeoutMillis = 30000
            connectTimeoutMillis = 15000
            socketTimeoutMillis = HttpTimeout.INFINITE_TIMEOUT_MS
        }
        install(HttpRedirect) {
            checkHttpMethod = false
        }
    }

    @Provides
    @Singleton
    fun provideSyncService(client: HttpClient): SyncService {
        return PocketBaseSyncService(client, PantryConstants.POCKETBASE_URL)
    }

    @Provides
    @Singleton
    fun provideOpenFoodFactsRepository(client: HttpClient): OpenFoodFactsRepository {
        return OpenFoodFactsRepository(client)
    }

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): PantryDatabase {
        return PantryDatabase.getDatabase(context)
    }

    @Provides
    fun providePantryDao(database: PantryDatabase): PantryDao = database.pantryDao()

    @Provides
    fun provideSyncQueueDao(database: PantryDatabase): SyncQueueDao = database.syncQueueDao()
}
