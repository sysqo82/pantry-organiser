package com.pantry.organiser.core.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.Serializable

@Entity(
    tableName = "past_items",
    indices = [
        Index(value = ["barcode"]),
    ],
)
@OptIn(InternalSerializationApi::class)
@Serializable
data class PastItem(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "barcode") val barcode: String? = null,
    @ColumnInfo(name = "brand") val brand: String? = null,
    @ColumnInfo(name = "package_quantity") val packageQuantity: String? = null,
    @ColumnInfo(name = "image_url") val imageUrl: String? = null,
    @ColumnInfo(name = "api_image_url") val apiImageUrl: String? = null,
    @ColumnInfo(name = "local_image_url") val localImageUrl: String? = null,
    @ColumnInfo(name = "local_image_uri") val localImageUri: String? = null,
    @ColumnInfo(name = "shelf_number") val shelfNumber: Int = 1,
    @ColumnInfo(name = "zone_index") val zoneIndex: Int = 1,
    @ColumnInfo(name = "tracking_type") val trackingType: TrackingType = TrackingType.BULK_LEVEL,
    @ColumnInfo(name = "sealed_count") val sealedCount: Int = 0,
    @ColumnInfo(name = "units_per_pack") val unitsPerPack: Int = 1,
    @ColumnInfo(name = "active_count") val activeCount: Int = 1,
    @ColumnInfo(name = "active_fill") val activeFill: FillLevel = FillLevel.FULL,
    @ColumnInfo(name = "is_assigned") val isAssigned: Boolean = false,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "updated_at") val updatedAt: Long = System.currentTimeMillis(),
)

fun PantryItem.toPastItem(): PastItem = PastItem(
    id = id,
    name = name,
    barcode = barcode,
    brand = brand,
    packageQuantity = packageQuantity,
    imageUrl = imageUrl,
    apiImageUrl = apiImageUrl,
    localImageUrl = localImageUrl,
    localImageUri = localImageUri,
    shelfNumber = shelfNumber,
    zoneIndex = zoneIndex,
    trackingType = trackingType,
    sealedCount = sealedCount,
    unitsPerPack = unitsPerPack,
    activeCount = activeCount,
    activeFill = activeFill,
    isAssigned = isAssigned,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

fun PastItem.toPantryItem(): PantryItem = PantryItem(
    id = id,
    name = name,
    barcode = barcode,
    brand = brand,
    packageQuantity = packageQuantity,
    imageUrl = imageUrl,
    apiImageUrl = apiImageUrl,
    localImageUrl = localImageUrl,
    localImageUri = localImageUri,
    shelfNumber = shelfNumber,
    zoneIndex = zoneIndex,
    trackingType = trackingType,
    sealedCount = sealedCount,
    unitsPerPack = unitsPerPack,
    activeCount = activeCount,
    activeFill = activeFill,
    isAssigned = isAssigned,
    createdAt = createdAt,
    updatedAt = updatedAt,
)
