package com.pantry.organiser.ingestion.widget

import android.content.Context
import androidx.datastore.core.CorruptionException
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.Serializer
import androidx.datastore.dataStoreFile
import androidx.glance.state.GlanceStateDefinition
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.InputStream
import java.io.OutputStream

@Serializable
data class WidgetShoppingItem(
    val id: String,
    val name: String,
    val brand: String? = null,
    val packageQuantity: String? = null,
    val barcode: String? = null
)

@Serializable
sealed interface ShoppingWidgetState {
    @Serializable
    object Loading : ShoppingWidgetState

    @Serializable
    data class Success(
        val items: List<WidgetShoppingItem>,
        val expandedItemId: String? = null,
        val statusMessage: String? = null,
        val lastUpdatedMillis: Long = System.currentTimeMillis()
    ) : ShoppingWidgetState

    @Serializable
    data class Error(
        val message: String
    ) : ShoppingWidgetState
}

object ShoppingWidgetStateSerializer : Serializer<ShoppingWidgetState> {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    override val defaultValue: ShoppingWidgetState = ShoppingWidgetState.Loading

    override suspend fun readFrom(input: InputStream): ShoppingWidgetState {
        return try {
            val text = input.readBytes().decodeToString()
            if (text.isBlank()) ShoppingWidgetState.Loading
            else json.decodeFromString(text)
        } catch (e: SerializationException) {
            throw CorruptionException("Cannot read ShoppingWidgetState JSON", e)
        } catch (e: Exception) {
            ShoppingWidgetState.Loading
        }
    }

    override suspend fun writeTo(t: ShoppingWidgetState, output: OutputStream) {
        val text = json.encodeToString(t)
        output.write(text.encodeToByteArray())
    }
}

object ShoppingWidgetStateDefinition : GlanceStateDefinition<ShoppingWidgetState> {
    private const val DATA_STORE_FILENAME = "shopping_list_widget_state"

    override suspend fun getDataStore(context: Context, fileKey: String): DataStore<ShoppingWidgetState> {
        return DataStoreFactory.create(
            serializer = ShoppingWidgetStateSerializer,
            produceFile = { context.dataStoreFile("${DATA_STORE_FILENAME}_$fileKey") }
        )
    }

    override fun getLocation(context: Context, fileKey: String): File {
        return context.dataStoreFile("${DATA_STORE_FILENAME}_$fileKey")
    }
}
