package com.pantry.organiser.data

import com.pantry.organiser.core.model.FillLevel
import com.pantry.organiser.core.model.PantryConstants
import com.pantry.organiser.core.model.PantryItem
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class PocketBasePantryItem(
    val id: String? = null,
    val name: String = "",
    val barcode: String? = null,
    val brand: String? = null,
    @SerialName("package_quantity") val packageQuantity: String? = null,
    @SerialName("image_url") val imageUrl: String? = null,
    @SerialName("local_image_uri") val localImageUri: String? = null,
    val image: String? = null, // PocketBase file field
    @SerialName("shelf_number") val shelfNumber: Int = 1,
    @SerialName("zone_index") val zoneIndex: Int = 1,
    @SerialName("sealed_count") val sealedCount: Int = 0,
    @SerialName("active_fill") val activeFill: String = "FULL",
    @SerialName("is_assigned") val isAssigned: Boolean = false,

    @SerialName("created") val created: String? = null,
    @SerialName("updated") val updated: String? = null
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
data class RealtimeEvent(
    val action: String, // "create", "update", "delete"
    val record: PocketBasePantryItem
)

private fun parsePocketBaseDate(dateStr: String?): Long {
    if (dateStr.isNullOrBlank()) return 0L
    return try {
        // PocketBase format: "2026-08-23 11:26:33.451Z" or "2026-08-23 11:26:33Z"
        // Try precise format first
        val formats = listOf(
            "yyyy-MM-dd HH:mm:ss.SSS'Z'",
            "yyyy-MM-dd HH:mm:ss'Z'",
            "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
            "yyyy-MM-dd'T'HH:mm:ss'Z'"
        )
        
        var parsedTime: Long? = null
        for (pattern in formats) {
            try {
                val format = java.text.SimpleDateFormat(pattern, java.util.Locale.US)
                format.timeZone = java.util.TimeZone.getTimeZone("UTC")
                parsedTime = format.parse(dateStr)?.time
                if (parsedTime != null) break
            } catch (_: Exception) {}
        }
        
        parsedTime ?: 0L
    } catch (e: Exception) {
        android.util.Log.e("PocketBaseModels", "Failed to parse date: $dateStr", e)
        0L
    }
}

fun PantryItem.toPocketBase(): PocketBasePantryItem {
    val pbShelf = shelfNumber.coerceIn(1, 4)
    val pbZone = zoneIndex.coerceIn(1, 3)
    
    // We trust the local trackingType. Classification logic moved to ViewModel.
    android.util.Log.d("PocketBaseModels", "Syncing $id to PB: shelf=$pbShelf, zone=$pbZone, trackingType=$trackingType")
    
    val validUrl = (apiImageUrl ?: imageUrl)?.takeIf { 
        it.isNotBlank() && (it.startsWith("http://") || it.startsWith("https://")) 
    }

    return PocketBasePantryItem(
        id = null,
        name = name.ifBlank { "Unknown Product" },
        barcode = barcode?.takeIf { it.isNotBlank() },
        brand = brand?.takeIf { it.isNotBlank() },
        packageQuantity = packageQuantity?.takeIf { it.isNotBlank() },
        imageUrl = validUrl,
        image = if (imageUrl == null) "" else null,
        localImageUri = localImageUri?.takeIf { it.isNotBlank() },
        shelfNumber = pbShelf,
        zoneIndex = pbZone,
        sealedCount = sealedCount,
        activeFill = activeFill.name,
        isAssigned = isAssigned
    )
}

fun PocketBasePantryItem.toLocal(): PantryItem {
    val updatedAtTime = parsePocketBaseDate(updated)
    val createdAtTime = parsePocketBaseDate(created)
    
    // The "image" field in PB holds the custom upload
    val customImageUrl = if (!image.isNullOrEmpty() && id != null) {
        val base = "${PantryConstants.POCKETBASE_URL}/api/files/pantry_items/$id/$image"
        if (updatedAtTime > 0) "$base?v=$updatedAtTime" else base
    } else null

    // The "image_url" field in PB holds the original OFF link
    val originalApiUrl = imageUrl?.ifBlank { null }?.takeIf { it != "N/A" }

    val mappedShelf = shelfNumber.coerceIn(1, 4)
    val mappedZone = zoneIndex.coerceIn(1, 3)
    
    val mappedTrackingType = PantryItem.determineTrackingType(name, quantity = packageQuantity)
    val inferredUnits = PantryItem.inferUnitsPerPack(name, packageQuantity)
    val rawFill = activeFill.ifBlank { "FULL" }
    
    return PantryItem(
        id = id.takeIf { !it.isNullOrBlank() } ?: ("local_" + java.util.UUID.randomUUID().toString()),
        name = name.ifBlank { "Unknown Product" },
        barcode = barcode?.ifBlank { null },
        brand = brand?.ifBlank { null },
        packageQuantity = packageQuantity?.ifBlank { null },
        imageUrl = customImageUrl, // Custom Photo from PB
        apiImageUrl = originalApiUrl, // Original Photo from API
        localImageUri = localImageUri?.ifBlank { null }, 
        shelfNumber = mappedShelf,
        zoneIndex = mappedZone,
        trackingType = mappedTrackingType,
        sealedCount = sealedCount,
        unitsPerPack = inferredUnits,
        activeCount = inferredUnits,
        activeFill = try { FillLevel.valueOf(rawFill) } catch (e: Exception) { FillLevel.FULL },
        isAssigned = isAssigned,
        createdAt = createdAtTime,
        updatedAt = updatedAtTime
    )
}
