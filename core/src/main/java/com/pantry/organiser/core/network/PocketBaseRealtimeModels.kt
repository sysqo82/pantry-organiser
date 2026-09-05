package com.pantry.organiser.core.network

import com.pantry.organiser.core.model.FillLevel
import com.pantry.organiser.core.model.PantryConstants
import com.pantry.organiser.core.model.PantryItem
import com.pantry.organiser.core.model.TrackingType
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import java.util.UUID

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
    @SerialName("api_image_url") val apiImageUrl: String? = null,
    @SerialName("local_image_url") val localImageUrl: String? = null,
    val image: String? = null,
    @SerialName("local_image_uri") val localImageUri: String? = null,
    @SerialName("shelf_number") val shelfNumber: Int = 1,
    @SerialName("zone_index") val zoneIndex: Int = 1,
    @SerialName("sealed_count") val sealedCount: Int = 0,
    @SerialName("units_per_pack") val unitsPerPack: Int? = null,
    @SerialName("active_count") val activeCount: Int? = null,
    @SerialName("active_fill") val activeFill: String = "FULL",
    @SerialName("is_assigned") val isAssigned: Boolean? = null,
    @SerialName("created") val created: String? = null,
    @SerialName("updated") val updated: String? = null
)

fun PantryItem.toPocketBase(): PocketBasePantryItem {
    val pbShelf = shelfNumber.coerceIn(1, 4)
    val pbZone = zoneIndex.coerceIn(1, 3)

    // OFF Image URL goes to image_url and api_image_url (NEVER PocketBase file URLs)
    val validOffUrl = (imageUrl ?: apiImageUrl)?.takeIf { 
        it.isNotBlank() && (it.startsWith("http://") || it.startsWith("https://")) && !it.contains("/api/files/") 
    } ?: ""

    // Custom photo URL goes to local_image_url
    val validCustomUrl = localImageUrl?.takeIf {
        it.isNotBlank() && (it.startsWith("http://") || it.startsWith("https://"))
    } ?: ""

    val validLocalUri = localImageUri?.takeIf { it.isNotBlank() } ?: ""

    return PocketBasePantryItem(
        id = id.takeIf { it.isNotBlank() && !it.startsWith("local_") },
        name = name.ifBlank { "Unknown Product" },
        barcode = barcode?.takeIf { it.isNotBlank() } ?: "",
        brand = brand?.takeIf { it.isNotBlank() } ?: "",
        packageQuantity = packageQuantity?.takeIf { it.isNotBlank() } ?: "",
        imageUrl = validOffUrl,
        apiImageUrl = validOffUrl,
        localImageUrl = validCustomUrl, // Send "" if null so PocketBase explicitly clears local_image_url column
        image = if (validCustomUrl.isBlank()) "" else null, // Send "" if clearing custom photo so PocketBase deletes the file attachment
        localImageUri = validLocalUri, // Send "" if null so PocketBase explicitly clears local_image_uri
        shelfNumber = pbShelf,
        zoneIndex = pbZone,
        sealedCount = sealedCount,
        unitsPerPack = unitsPerPack,
        activeCount = activeCount,
        activeFill = activeFill.name,
        isAssigned = isAssigned
    )
}

fun PocketBasePantryItem.toLocal(): PantryItem {
    val rawShelf = shelfNumber
    val rawZone = zoneIndex
    val mappedShelf = rawShelf.coerceIn(1, 4)
    val mappedZone = rawZone.coerceIn(1, 3)

    val inferredUnits = unitsPerPack ?: PantryItem.inferUnitsPerPack(name, packageQuantity)
    val effectiveActiveCount = activeCount ?: inferredUnits
    val mappedTrackingType = PantryItem.determineTrackingType(name, quantity = packageQuantity)
    val rawFill = activeFill.ifBlank { "FULL" }

    val effectiveIsAssigned = isAssigned ?: false

    val hostedUrlFromImageFile = if (!id.isNullOrBlank() && !image.isNullOrBlank()) {
        "https://pantry.lockpc.co.uk/api/files/pantry_items/$id/$image"
    } else null

    // Determine custom localImageUrl (from local_image_url column, image file attachment, or legacy image_url with /api/files/)
    val resolvedCustomUrl = localImageUrl?.takeIf { it.isNotBlank() && it != "N/A" }
        ?: hostedUrlFromImageFile
        ?: imageUrl?.takeIf { it.contains("/api/files/") }

    // Determine original OFF imageUrl (from image_url or api_image_url column, if NOT a PocketBase file URL)
    val resolvedOffUrl = (apiImageUrl ?: imageUrl)?.takeIf { 
        it.isNotBlank() && it != "N/A" && !it.contains("/api/files/") 
    }

    return PantryItem(
        id = id.takeIf { !it.isNullOrBlank() } ?: ("local_" + UUID.randomUUID().toString()),
        name = name.ifBlank { "Unknown Product" },
        barcode = barcode?.ifBlank { null },
        brand = brand?.ifBlank { null },
        packageQuantity = packageQuantity?.ifBlank { null },
        imageUrl = resolvedOffUrl,
        apiImageUrl = resolvedOffUrl,
        localImageUrl = resolvedCustomUrl,
        localImageUri = localImageUri?.takeIf { it.isNotBlank() && it != "N/A" },
        shelfNumber = mappedShelf,
        zoneIndex = mappedZone,
        trackingType = mappedTrackingType,
        sealedCount = sealedCount,
        unitsPerPack = inferredUnits,
        activeCount = effectiveActiveCount,
        activeFill = try { FillLevel.valueOf(rawFill) } catch (e: Exception) { FillLevel.FULL },
        isAssigned = effectiveIsAssigned
    )
}
