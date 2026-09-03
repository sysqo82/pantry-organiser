package com.pantry.organiser.core.model

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
    @PrimaryKey val id: String,
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "barcode") val barcode: String? = null,
    @ColumnInfo(name = "brand") val brand: String? = null,
    @ColumnInfo(name = "package_quantity") val packageQuantity: String? = null,
    @ColumnInfo(name = "image_url") val imageUrl: String? = null,
    @ColumnInfo(name = "api_image_url") val apiImageUrl: String? = null,
    @ColumnInfo(name = "local_image_uri") val localImageUri: String? = null,
    @ColumnInfo(name = "shelf_number") val shelfNumber: Int,
    @ColumnInfo(name = "zone_index") val zoneIndex: Int,
    @ColumnInfo(name = "tracking_type") val trackingType: TrackingType = TrackingType.BULK_LEVEL,
    @ColumnInfo(name = "sealed_count") val sealedCount: Int = 0,
    @ColumnInfo(name = "units_per_pack") val unitsPerPack: Int = 1,
    @ColumnInfo(name = "active_count") val activeCount: Int = 1,
    @ColumnInfo(name = "active_fill") val activeFill: FillLevel = FillLevel.FULL,
    @ColumnInfo(name = "is_assigned") val isAssigned: Boolean = false,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "updated_at") val updatedAt: Long = System.currentTimeMillis()
) {
    val safeShelfNumber: Int get() = shelfNumber.coerceIn(1, 4)
    val safeZoneIndex: Int get() = zoneIndex.coerceIn(1, 3)

    val totalDisplayCount: Int get() = when (trackingType) {
        TrackingType.DISCRETE_COUNT -> {
            if (unitsPerPack > 1) {
                (sealedCount * unitsPerPack) + activeCount
            } else {
                sealedCount
            }
        }
        TrackingType.BULK_LEVEL -> if (activeFill != FillLevel.EMPTY) sealedCount + 1 else sealedCount
    }

    val hasStock: Boolean get() = totalDisplayCount > 0

    fun getDisplayUnitLabel(isPlural: Boolean = totalDisplayCount != 1): String {
        return when {
            trackingType == TrackingType.BULK_LEVEL -> if (isPlural) "Portions" else "Portion"
            unitsPerPack > 1 -> if (isPlural) "Cans/Tins" else "Can/Tin"
            else -> if (isPlural) "Units" else "Unit"
        }
    }

    companion object {
        fun inferUnitsPerPack(name: String?, quantity: String?): Int {
            val searchString = "${name ?: ""} ${quantity ?: ""}".lowercase()
            val multiplierRegex = """(\d+)\s*[x×*]\s*\d+""".toRegex()
            multiplierRegex.find(searchString)?.let {
                val val1 = it.groupValues[1].toIntOrNull() ?: 1
                if (val1 in 2..48) return val1
            }
            val packOfRegex = """pack of (\d+)""".toRegex()
            packOfRegex.find(searchString)?.let { return it.groupValues[1].toIntOrNull() ?: 1 }

            val numberPackRegex = """(\d+)\s*-?pack\b""".toRegex()
            numberPackRegex.find(searchString)?.let { return it.groupValues[1].toIntOrNull() ?: 1 }

            val pkRegex = """(\d+)\s*pk\b""".toRegex()
            pkRegex.find(searchString)?.let { return it.groupValues[1].toIntOrNull() ?: 1 }

            val containersRegex = """(\d+)\s*(tins|cans|jars|bottles|pots)\b""".toRegex()
            containersRegex.find(searchString)?.let { return it.groupValues[1].toIntOrNull() ?: 1 }

            val endNumberRegex = """\b(\d{1,2})$""".toRegex()
            endNumberRegex.find(searchString.trim())?.let {
                val val1 = it.groupValues[1].toIntOrNull() ?: 1
                if (val1 in 2..12) return val1
            }

            return 1
        }

        fun determineTrackingType(
            name: String,
            categories: List<String>? = null,
            quantity: String? = null,
            unitsPerPack: Int = inferUnitsPerPack(name, quantity)
        ): TrackingType {
            if (unitsPerPack > 1) {
                return TrackingType.DISCRETE_COUNT
            }

            val categoriesCombined = categories?.joinToString(" ")?.lowercase() ?: ""
            val quantityLower = quantity?.lowercase() ?: ""
            val nameLower = name.lowercase()

            val stapleRegex = Regex(
                "\\b(flour|sugar|rice|pasta|pastas|spaghetti|bucatini|penne|fusilli|farfalle|macaroni|rigatoni|linguine|tagliatelle|fettuccine|lasagne|lasagna|orzo|gnocchi|cannelloni|tortellini|ravioli|vermicelli|rotini|cavatappi|conchiglie|pappardelle|noodles?|oats|oatmeal|porridge|couscous|quinoa|lentils|bulgur|polenta|semolina|barley)\\b"
            )
            val isDryStaple = stapleRegex.containsMatchIn(nameLower) || stapleRegex.containsMatchIn(categoriesCombined)
            val isContainerOrSauce = nameLower.contains(Regex("\\b(can|tin|jar|bottle|sauce|spray)\\b"))

            if (isDryStaple && !isContainerOrSauce) {
                return TrackingType.BULK_LEVEL
            }

            val forceDiscreteKeywords = listOf(
                "sauce", "soy sauce", "ketchup", "mayonnaise", "mustard", 
                "vinegar", "dressing", "can", "tin", "jar", "bottle", "spray",
                "beans", "soup", "tuna", "sweetcorn", "corn", "tomatoes", "multipack", "tins", "cans", "bottles", "jars"
            )

            if (forceDiscreteKeywords.any { keyword ->
                val regex = Regex("\\b${Regex.escape(keyword)}\\b")
                categoriesCombined.contains(regex) || 
                quantityLower.contains(regex) || 
                nameLower.contains(regex)
            }) {
                return TrackingType.DISCRETE_COUNT
            }

            val strictBulkKeywords = listOf(
                "cooking oil", "olive oil", "vegetable oil", "sunflower oil", "cereal",
                "baking powder", "baking soda", "yeast", "salt", "spice", "spices", "seasoning"
            )

            if (strictBulkKeywords.any { keyword ->
                val regex = Regex("\\b${Regex.escape(keyword)}\\b")
                categoriesCombined.contains(regex) || nameLower.contains(regex)
            }) {
                return TrackingType.BULK_LEVEL
            }

            if (quantityLower.isNotEmpty()) {
                val hasLargeUnit = quantityLower.contains("kg") || 
                                 quantityLower.contains(" l ") || 
                                 quantityLower.contains("liter") ||
                                 quantityLower.contains("litre")
                
                if (hasLargeUnit) return TrackingType.BULK_LEVEL
                
                val regex = """(\d+)\s*(g|ml)""".toRegex()
                val match = regex.find(quantityLower)
                if (match != null) {
                    val value = match.groupValues[1].toIntOrNull() ?: 0
                    if (value >= 1000) return TrackingType.BULK_LEVEL
                }
            }

            return TrackingType.DISCRETE_COUNT
        }
    }
}

class PantryTypeConverters {
    @TypeConverter fun fromTrackingType(type: TrackingType): String = type.name
    @TypeConverter fun toTrackingType(value: String): TrackingType = try { TrackingType.valueOf(value) } catch (e: Exception) { TrackingType.BULK_LEVEL }
    @TypeConverter fun fromFillLevel(level: FillLevel): String = level.name
    @TypeConverter fun toFillLevel(value: String): FillLevel = try { FillLevel.valueOf(value) } catch (e: Exception) { FillLevel.FULL }
}
