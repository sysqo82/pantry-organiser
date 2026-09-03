package com.pantry.organiser.ingestion.di

import com.pantry.organiser.core.model.PantryConstants
import com.pantry.organiser.core.network.OpenFoodFactsRepository
import com.pantry.organiser.core.network.PocketBaseSyncService
import com.pantry.organiser.core.network.SyncService
import com.pantry.organiser.ingestion.AndroidFeedbackController
import com.pantry.organiser.ingestion.FeedbackController
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
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
abstract class IngestionModule {

    @Binds
    @Singleton
    abstract fun bindFeedbackController(impl: AndroidFeedbackController): FeedbackController

    companion object {
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
                checkHttpMethod = false // Follow redirects for POST too
            }
        }

        @Provides
        @Singleton
        fun provideSyncService(client: HttpClient): SyncService {
            // In a real app, baseUrl would come from build config or settings
            return PocketBaseSyncService(client, PantryConstants.POCKETBASE_URL)
        }

        @Provides
        @Singleton
        fun provideOpenFoodFactsRepository(client: HttpClient): OpenFoodFactsRepository {
            return OpenFoodFactsRepository(client)
        }
    }
}
