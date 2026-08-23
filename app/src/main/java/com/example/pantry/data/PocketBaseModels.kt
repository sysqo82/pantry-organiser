package com.pantry.organiser.data

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
    // localImageUri REMOVED to prevent cross-device path leakage
    val image: String? = null, // PocketBase file field
    @SerialName("shelf_number") val shelfNumber: Int = 1,
    @SerialName("zone_index") val zoneIndex: Int = 1,
    @SerialName("tracking_type") val trackingType: String = "DISCRETE_COUNT", // "DISCRETE_COUNT" | "BULK_LEVEL"
    @SerialName("sealed_count") val sealedCount: Int = 0,
    @SerialName("active_fill") val activeFill: String = "FULL", // enum names
    @SerialName("created") val created: String? = null,
    @SerialName("updated") val updated: String? = null
)

@Serializable
data class PocketBaseListResponse<T>(
    val page: Int,
    val perPage: Int,
    val totalItems: Int,
    val totalPages: Int,
    val items: List<T>
)

@Serializable
data class RealtimeEvent(
    val action: String, // "create", "update", "delete"
    val record: PocketBasePantryItem
)

const val POCKETBASE_URL = "https://pantry.lockpc.co.uk"

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
    
    android.util.Log.d("PocketBaseModels", "Syncing $id to PB: shelf=$pbShelf, zone=$pbZone")
    
    return PocketBasePantryItem(
        id = id.takeIf { it.isNotEmpty() && !it.startsWith("local_") },
        name = name,
        barcode = barcode,
        brand = brand,
        packageQuantity = packageQuantity,
        imageUrl = imageUrl?.takeIf { it.isNotBlank() && it != "N/A" },
        shelfNumber = pbShelf,
        zoneIndex = pbZone,
        trackingType = trackingType.name,
        sealedCount = sealedCount,
        activeFill = activeFill.name
    )
}

fun PocketBasePantryItem.toLocal(): PantryItem {
    val updatedAtTime = parsePocketBaseDate(updated)
    
    val remoteImageUrl = if (!image.isNullOrEmpty() && id != null) {
        // Append version parameter to force cache refresh when image is replaced
        val base = "$POCKETBASE_URL/api/files/pantry_items/$id/$image"
        if (updatedAtTime > 0) "$base?v=$updatedAtTime" else base
    } else {
        imageUrl?.takeIf { it.isNotBlank() && it != "N/A" }
    }

    val mappedShelf = shelfNumber.coerceIn(1, 4)
    val mappedZone = zoneIndex.coerceIn(1, 3)
    
    // Explicitly parse tracking type to see what's coming from server
    val mappedTrackingType = try { 
        TrackingType.valueOf(trackingType) 
    } catch (e: Exception) { 
        android.util.Log.e("PocketBaseModels", "Failed to parse trackingType: '$trackingType' for item $id, defaulting to DISCRETE_COUNT")
        TrackingType.DISCRETE_COUNT 
    }
    
    android.util.Log.d("PocketBaseModels", "Mapping $id: shelf=$mappedShelf, zone=$mappedZone, trackingType=$mappedTrackingType (raw='$trackingType')")

    return PantryItem(
        id = id ?: "",
        name = name,
        barcode = barcode,
        brand = brand,
        packageQuantity = packageQuantity,
        imageUrl = remoteImageUrl,
        localImageUri = null, // Local URI is managed strictly by repository merge logic
        shelfNumber = mappedShelf,
        zoneIndex = mappedZone,
        trackingType = mappedTrackingType,
        sealedCount = sealedCount,
        activeFill = try { FillLevel.valueOf(activeFill) } catch (e: Exception) { FillLevel.FULL },
        createdAt = parsePocketBaseDate(created),
        updatedAt = updatedAtTime
    )
}
