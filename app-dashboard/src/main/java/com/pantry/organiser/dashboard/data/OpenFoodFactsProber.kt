package com.pantry.organiser.dashboard.data

import android.util.Log
import com.pantry.organiser.core.model.PantryItem
import com.pantry.organiser.core.network.OffProduct
import com.pantry.organiser.core.network.OpenFoodFactsRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.firstOrNull
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OpenFoodFactsProber @Inject constructor(
    private val pantryRepository: PantryRepository,
    private val openFoodFactsRepository: OpenFoodFactsRepository
) {
    suspend fun probeAndSync(itemDelayMs: Long = 500L): Int {
        val items = pantryRepository.allItems.firstOrNull() ?: emptyList()
        val itemsToProbe = items.filter { !it.barcode.isNullOrBlank() }

        var updatedCount = 0

        for ((index, item) in itemsToProbe.withIndex()) {
            val barcode = item.barcode ?: continue

            // Rate-limiting delay between requests to prevent HTTP 429
            if (index > 0 && itemDelayMs > 0) {
                delay(itemDelayMs)
            }

            try {
                val offProduct = openFoodFactsRepository.getProduct(barcode)
                if (offProduct != null) {
                    val mergedItem = probeAndMergeItem(item, offProduct)
                    if (mergedItem != null) {
                        Log.i("OFFProber", "Updating item ${item.id} (${item.name}) from OFF probe")
                        pantryRepository.updateItem(mergedItem)
                        updatedCount++
                    }
                }
            } catch (e: Exception) {
                Log.e("OFFProber", "Error probing barcode $barcode: ${e.message}")
            }
        }

        return updatedCount
    }

    companion object {
        fun probeAndMergeItem(
            item: PantryItem,
            offProduct: OffProduct
        ): PantryItem? {
            val barcode = item.barcode ?: return null

            val newName = offProduct.displayProductName?.takeIf { it.isNotBlank() }
            val newBrand = offProduct.displayBrands?.takeIf { it.isNotBlank() }
            val newQuantity = offProduct.weight?.takeIf { it.isNotBlank() }
            val newApiImageUrl = offProduct.imageUrl?.takeIf { it.isNotBlank() && it != "N/A" }
            val inferredUnits = offProduct.inferUnitsPerPack()

            val inferredTrackingType = PantryItem.determineTrackingType(
                name = newName ?: item.name,
                categories = offProduct.categoriesTags,
                quantity = newQuantity ?: item.packageQuantity,
                unitsPerPack = inferredUnits
            )

            // Placeholder name check
            val isPlaceholderName = item.name.isBlank() ||
                    item.name == "Unknown Product" ||
                    item.name == "Unnamed Item" ||
                    item.name == "Network Error" ||
                    item.name == "Enriching..."

            // Check if item has NO identifiable entries (Rule 1)
            val hasNoIdentifiableEntries = isPlaceholderName ||
                    (item.brand.isNullOrBlank() &&
                            item.packageQuantity.isNullOrBlank() &&
                            item.imageUrl.isNullOrBlank() &&
                            item.apiImageUrl.isNullOrBlank())

            val hasLocalImage = !item.localImageUri.isNullOrBlank() ||
                    (!item.localImageUrl.isNullOrBlank() && item.localImageUrl != "N/A")

            if (hasNoIdentifiableEntries) {
                // Rule 1: Update everything from Open Food Facts
                val effectiveName = newName ?: item.name.ifBlank { "Unknown Product" }
                val effectiveBrand = newBrand ?: item.brand
                val effectiveQuantity = newQuantity ?: item.packageQuantity
                val effectiveUnitsPerPack = if (inferredUnits > 1) inferredUnits else item.unitsPerPack

                val updatedApiImageUrl = newApiImageUrl ?: item.apiImageUrl
                val updatedImageUrl = if (hasLocalImage) item.imageUrl else (newApiImageUrl ?: item.imageUrl)

                val updatedItem = item.copy(
                    name = effectiveName,
                    brand = effectiveBrand,
                    packageQuantity = effectiveQuantity,
                    apiImageUrl = updatedApiImageUrl,
                    imageUrl = updatedImageUrl,
                    unitsPerPack = effectiveUnitsPerPack,
                    trackingType = inferredTrackingType,
                    updatedAt = System.currentTimeMillis()
                )

                return if (updatedItem != item) updatedItem else null
            } else {
                // Rule 2 & Rule 3: Existing OFF product -> update only if there are new/changed entries
                var updated = false

                var updatedName = item.name
                if (isPlaceholderName && newName != null) {
                    updatedName = newName
                    updated = true
                }

                var updatedBrand = item.brand
                if (!newBrand.isNullOrBlank() && newBrand != item.brand) {
                    updatedBrand = newBrand
                    updated = true
                }

                var updatedQuantity = item.packageQuantity
                if (!newQuantity.isNullOrBlank() && newQuantity != item.packageQuantity) {
                    updatedQuantity = newQuantity
                    updated = true
                }

                var updatedUnitsPerPack = item.unitsPerPack
                if (inferredUnits > 1 && inferredUnits != item.unitsPerPack) {
                    updatedUnitsPerPack = inferredUnits
                    updated = true
                }

                var updatedApiImageUrl = item.apiImageUrl
                var updatedImageUrl = item.imageUrl

                if (!newApiImageUrl.isNullOrBlank() && newApiImageUrl != item.apiImageUrl) {
                    updatedApiImageUrl = newApiImageUrl
                    updated = true
                    // Rule 3: Do NOT overwrite imageUrl if item has a local image
                    if (!hasLocalImage) {
                        updatedImageUrl = newApiImageUrl
                    }
                }

                if (updated) {
                    return item.copy(
                        name = updatedName,
                        brand = updatedBrand,
                        packageQuantity = updatedQuantity,
                        apiImageUrl = updatedApiImageUrl,
                        imageUrl = updatedImageUrl,
                        unitsPerPack = updatedUnitsPerPack,
                        trackingType = if (inferredUnits > 1) inferredTrackingType else item.trackingType,
                        updatedAt = System.currentTimeMillis()
                    )
                } else {
                    return null
                }
            }
        }
    }
}
