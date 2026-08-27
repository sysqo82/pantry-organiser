package com.pantry.organiser.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import kotlinx.serialization.Serializable

@Serializable
enum class TrackingType {
    DISCRETE_COUNT, BULK_LEVEL
}

@Serializable
enum class FillLevel(val percentage: Int, val label: String) {
    EMPTY(0, "Empty"),
    LOW(25, "Low"),
    HALF(50, "Half"),
    THREE_QUARTERS(75, "75%"),
    FULL(100, "Full");

    fun next(): FillLevel = when (this) {
        EMPTY -> LOW
        LOW -> HALF
        HALF -> THREE_QUARTERS
        THREE_QUARTERS -> FULL
        FULL -> FULL
    }

    fun prev(): FillLevel = when (this) {
        FULL -> THREE_QUARTERS
        THREE_QUARTERS -> HALF
        HALF -> LOW
        LOW -> EMPTY
        EMPTY -> EMPTY
    }
}

@Entity(
    tableName = "pantry_items",
    indices = [
        Index(value = ["barcode"]),
        Index(value = ["shelf_number", "zone_index"])
    ]
)
@Serializable
data class PantryItem(
    @PrimaryKey val id: String, // PocketBase ID
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "barcode") val barcode: String? = null,
    @ColumnInfo(name = "brand") val brand: String? = null,
    @ColumnInfo(name = "package_quantity") val packageQuantity: String? = null,
    @ColumnInfo(name = "image_url") val imageUrl: String? = null, // Custom/File URL
    @ColumnInfo(name = "api_image_url") val apiImageUrl: String? = null, // Original OFF URL
    @ColumnInfo(name = "local_image_uri") val localImageUri: String? = null,
    @ColumnInfo(name = "shelf_number") val shelfNumber: Int, // 1 to 4 (S1 to S4)
    @ColumnInfo(name = "zone_index") val zoneIndex: Int,     // 1: Left, 2: Mid, 3: Right
    @ColumnInfo(name = "tracking_type") val trackingType: TrackingType = TrackingType.BULK_LEVEL,
    @ColumnInfo(name = "sealed_count") val sealedCount: Int = 0,
    @ColumnInfo(name = "units_per_pack") val unitsPerPack: Int = 1,
    @ColumnInfo(name = "active_count") val activeCount: Int = 1,
    @ColumnInfo(name = "active_fill") val activeFill: FillLevel = FillLevel.FULL,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "updated_at") val updatedAt: Long = System.currentTimeMillis()
) {
    // Derived properties for UI consistency - strictly enforcing 1-based indexing
    val safeShelfNumber: Int get() = shelfNumber.coerceIn(1, 4)
    val safeZoneIndex: Int get() = zoneIndex.coerceIn(1, 3)

    companion object {
        /**
         * Heuristic to extract bundle size (e.g. "4 x 100g", "3x80g", "4x 200ml", "4 Pack", "Pack of 6")
         */
        fun inferUnitsPerPack(name: String?, quantity: String?): Int {
            val searchString = "${name ?: ""} ${quantity ?: ""}".lowercase()
            
            // 1. Multiplier patterns: "4 x 100g", "3x80g", "4x 200ml", "3 * 100g"
            val multiplierRegex = """(\d+)\s*[x×*]\s*\d+""".toRegex()
            multiplierRegex.find(searchString)?.let {
                val val1 = it.groupValues[1].toIntOrNull() ?: 1
                if (val1 in 2..48) return val1
            }
            
            // 2. "Pack of" patterns: "Pack of 4", "Pack of 6"
            val packOfRegex = """pack of (\d+)""".toRegex()
            packOfRegex.find(searchString)?.let {
                return it.groupValues[1].toIntOrNull() ?: 1
            }

            // 3. Number before "Pack" patterns: "4 pack", "6 pack", "4-pack"
            val numberPackRegex = """(\d+)\s*-?pack\b""".toRegex()
            numberPackRegex.find(searchString)?.let {
                return it.groupValues[1].toIntOrNull() ?: 1
            }

            // 4. Compact "pk" patterns: "4pk", "6pk"
            val pkRegex = """(\d+)\s*pk\b""".toRegex()
            pkRegex.find(searchString)?.let {
                return it.groupValues[1].toIntOrNull() ?: 1
            }

            // 5. Tins/Cans patterns: "3 tins", "4 cans"
            val containersRegex = """(\d+)\s*(tins|cans|jars|bottles|pots)\b""".toRegex()
            containersRegex.find(searchString)?.let {
                return it.groupValues[1].toIntOrNull() ?: 1
            }
            
            // 6. Loose multiplier at end: "Sweetcorn 3" or "Yogurt 4" (dangerous but user requested robustness)
            // Only if it's a small number at the end of a string
            val endNumberRegex = """\b(\d{1,2})$""".toRegex()
            endNumberRegex.find(searchString.trim())?.let {
                val val1 = it.groupValues[1].toIntOrNull() ?: 1
                if (val1 in 2..12) return val1
            }
            
            return 1
        }


    }
}


class PantryTypeConverters {
    @TypeConverter fun fromTrackingType(type: TrackingType): String = type.name
    @TypeConverter fun toTrackingType(value: String): TrackingType = try { TrackingType.valueOf(value) } catch (e: Exception) { TrackingType.BULK_LEVEL }
    @TypeConverter fun fromFillLevel(level: FillLevel): String = level.name
    @TypeConverter fun toFillLevel(value: String): FillLevel = try { FillLevel.valueOf(value) } catch (e: Exception) { FillLevel.FULL }
}
