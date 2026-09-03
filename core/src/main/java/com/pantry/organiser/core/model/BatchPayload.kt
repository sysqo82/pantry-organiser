package com.pantry.organiser.core.model

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.*

@Serializable(with = BatchPayloadSerializer::class)
data class BatchPayload(
    val pantryId: String,
    val itemIds: List<String> = emptyList(),
    val items: List<ScannedItem> = emptyList(),
    val timestamp: Long = System.currentTimeMillis()
) {
    val safeItemIds: List<String> get() = itemIds
    val safeItems: List<ScannedItem> get() = items
}

@Serializable
data class ScannedItem(
    @SerialName("barcode") val barcode: String,
    @SerialName("timestamp") val timestamp: Long = System.currentTimeMillis(),
    @SerialName("productName") val productName: String = "",
    @SerialName("brand") val brand: String = "",
    @SerialName("imageUrl") val imageUrl: String = "",
    @SerialName("quantity") val quantity: String = ""
)

object BatchPayloadSerializer : KSerializer<BatchPayload> {
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    override val descriptor: SerialDescriptor = JsonObject.serializer().descriptor

    override fun serialize(encoder: Encoder, value: BatchPayload) {
        val jsonEncoder = encoder as? JsonEncoder ?: throw IllegalStateException("Expected JsonEncoder")
        val itemsElement: JsonElement = when {
            value.itemIds.isNotEmpty() -> JsonArray(value.itemIds.map { JsonPrimitive(it) })
            value.items.isNotEmpty() -> json.encodeToJsonElement(value.items)
            else -> JsonArray(emptyList())
        }
        val obj = buildJsonObject {
            put("pantryId", value.pantryId)
            put("items", itemsElement)
            put("timestamp", value.timestamp)
        }
        jsonEncoder.encodeJsonElement(obj)
    }

    override fun deserialize(decoder: Decoder): BatchPayload {
        val jsonDecoder = decoder as? JsonDecoder ?: throw IllegalStateException("Expected JsonDecoder")
        val obj = jsonDecoder.decodeJsonElement().jsonObject

        val pantryId = obj["pantryId"]?.jsonPrimitive?.content ?: "default-pantry"
        val timestamp = obj["timestamp"]?.jsonPrimitive?.longOrNull ?: System.currentTimeMillis()

        var parsedItemIds: List<String> = emptyList()
        var parsedItems: List<ScannedItem> = emptyList()

        val itemsElement = obj["items"]
        if (itemsElement is JsonArray) {
            val first = itemsElement.firstOrNull()
            if (first is JsonPrimitive && first.isString) {
                parsedItemIds = itemsElement.mapNotNull { it.jsonPrimitive.contentOrNull }
            } else if (first is JsonObject) {
                parsedItems = try {
                    json.decodeFromJsonElement<List<ScannedItem>>(itemsElement)
                } catch (_: Exception) {
                    emptyList()
                }
            }
        }

        return BatchPayload(
            pantryId = pantryId,
            itemIds = parsedItemIds,
            items = parsedItems,
            timestamp = timestamp
        )
    }
}
