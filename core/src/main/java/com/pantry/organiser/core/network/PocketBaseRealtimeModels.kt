package com.pantry.organiser.core.network

import com.pantry.organiser.core.model.FillLevel
import com.pantry.organiser.core.model.PantryConstants
import com.pantry.organiser.core.model.PantryItem
import com.pantry.organiser.core.model.TrackingType
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class RealtimeEvent(
    val action: String,
    val record: JsonElement
)

@Serializable
data class RealtimeConnectMessage(
    val clientId: String
)

@Serializable
data class RealtimeSubscribeRequest(
    val clientId: String,
    val subscriptions: List<String>
)

@Serializable
data class PocketBaseListResponse<T>(
    val page: Int = 1,
    val perPage: Int = 30,
    val totalItems: Int = 0,
    val totalPages: Int = 0,
    val items: List<T> = emptyList()
)

@Serializable
data class PocketBasePantryItem(
    val id: String? = null,
    val name: String = "",
    val barcode: String? = null,
    val brand: String? = null,
    @SerialName("package_quantity") val packageQuantity: String? = null,
    @SerialName("image_url") val imageUrl: String? = null,
    val image: String? = null,
    @SerialName("local_image_uri") val localImageUri: String? = null,
    @SerialName("shelf_number") val shelfNumber: Int = 1,
    @SerialName("zone_index") val zoneIndex: Int = 1,
    @SerialName("sealed_count") val sealedCount: Int = 0,
    @SerialName("active_fill") val activeFill: String = "FULL",
    @SerialName("is_assigned") val isAssigned: Boolean? = null,
    @SerialName("created") val created: String? = null,
    @SerialName("updated") val updated: String? = null
)

fun PantryItem.toPocketBase(): PocketBasePantryItem {
    val pbShelf = shelfNumber.coerceIn(1, 4)
    val pbZone = zoneIndex.coerceIn(1, 3)

    val validUrl = (apiImageUrl ?: imageUrl)?.takeIf { 
        it.isNotBlank() && (it.startsWith("http://") || it.startsWith("https://")) 
    }

    return PocketBasePantryItem(
        id = id.takeIf { it.isNotBlank() && !it.startsWith("local_") },
        name = name.ifBlank { "Unknown Product" },
        barcode = barcode?.takeIf { it.isNotBlank() },
        brand = brand?.takeIf { it.isNotBlank() },
        packageQuantity = packageQuantity?.takeIf { it.isNotBlank() },
        imageUrl = validUrl,
        image = null,
        localImageUri = localImageUri?.takeIf { it.isNotBlank() },
        shelfNumber = pbShelf,
        zoneIndex = pbZone,
        sealedCount = sealedCount,
        activeFill = activeFill.name,
        isAssigned = isAssigned
    )
}

fun PocketBasePantryItem.toLocal(): PantryItem {
    val rawShelf = shelfNumber
    val rawZone = zoneIndex
    val mappedShelf = rawShelf.coerceIn(1, 4)
    val mappedZone = rawZone.coerceIn(1, 3)

    val inferredUnits = PantryItem.inferUnitsPerPack(name, packageQuantity)
    val mappedTrackingType = PantryItem.determineTrackingType(name, quantity = packageQuantity)
    val rawFill = activeFill.ifBlank { "FULL" }

    val effectiveIsAssigned = isAssigned ?: false

    return PantryItem(
        id = id.takeIf { !it.isNullOrBlank() } ?: ("local_" + java.util.UUID.randomUUID().toString()),
        name = name.ifBlank { "Unknown Product" },
        barcode = barcode?.ifBlank { null },
        brand = brand?.ifBlank { null },
        packageQuantity = packageQuantity?.ifBlank { null },
        imageUrl = imageUrl?.ifBlank { null },
        apiImageUrl = imageUrl?.ifBlank { null },
        localImageUri = localImageUri?.ifBlank { null },
        shelfNumber = mappedShelf,
        zoneIndex = mappedZone,
        trackingType = mappedTrackingType,
        sealedCount = sealedCount,
        unitsPerPack = inferredUnits,
        activeCount = inferredUnits,
        activeFill = try { FillLevel.valueOf(rawFill) } catch (e: Exception) { FillLevel.FULL },
        isAssigned = effectiveIsAssigned
    )
}
