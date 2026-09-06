package com.pantry.organiser.core.model

enum class SuggestionType {
    CONSUMPTION,
    RESTOCK,
    LOCATION,
    TRACKING
}

data class SmartInventorySuggestion(
    val type: SuggestionType,
    val title: String,
    val description: String,
    val priority: Int = 0
)

object InventorySuggestionEngine {

    fun generateSuggestions(item: PantryItem): List<SmartInventorySuggestion> {
        val suggestions = mutableListOf<SmartInventorySuggestion>()

        // 1. Consumption Suggestions
        if (item.trackingType == TrackingType.BULK_LEVEL) {
            when (item.activeFill) {
                FillLevel.LOW -> suggestions.add(
                    SmartInventorySuggestion(
                        type = SuggestionType.CONSUMPTION,
                        title = "Active Pack Running Low",
                        description = "Active container is at 25% (Low). Prioritize using this before opening a sealed pack.",
                        priority = 10
                    )
                )
                FillLevel.HALF -> suggestions.add(
                    SmartInventorySuggestion(
                        type = SuggestionType.CONSUMPTION,
                        title = "Active Pack Half Full",
                        description = "Active container is at 50%. Good condition for current usage.",
                        priority = 5
                    )
                )
                FillLevel.EMPTY -> if (item.sealedCount > 0) {
                    suggestions.add(
                        SmartInventorySuggestion(
                            type = SuggestionType.CONSUMPTION,
                            title = "Active Pack Empty",
                            description = "Active container is empty. Open 1 of the ${item.sealedCount} sealed pack(s) in stock.",
                            priority = 10
                        )
                    )
                }
                else -> {}
            }
        } else if (item.trackingType == TrackingType.DISCRETE_COUNT) {
            if (item.activeCount in 1..2 && item.unitsPerPack > 2) {
                suggestions.add(
                    SmartInventorySuggestion(
                        type = SuggestionType.CONSUMPTION,
                        title = "Pack Almost Finished",
                        description = "Only ${item.activeCount} unit(s) remaining in active pack.",
                        priority = 8
                    )
                )
            }
        }

        // 2. Restock / Quantity Suggestions
        if (!item.hasStock) {
            suggestions.add(
                SmartInventorySuggestion(
                    type = SuggestionType.RESTOCK,
                    title = "Out of Stock",
                    description = "This item is currently out of stock in your pantry. Consider adding to restock list.",
                    priority = 15
                )
            )
        } else if (item.sealedCount == 0) {
            suggestions.add(
                SmartInventorySuggestion(
                    type = SuggestionType.RESTOCK,
                    title = "No Sealed Backups",
                    description = "You are down to your last active pack with 0 sealed backups remaining.",
                    priority = 9
                )
            )
        } else if (item.sealedCount >= 3) {
            suggestions.add(
                SmartInventorySuggestion(
                    type = SuggestionType.RESTOCK,
                    title = "Well Stocked",
                    description = "You have ${item.sealedCount} sealed backups available. Excellent for meal prep or high-volume usage.",
                    priority = 2
                )
            )
        }

        // 3. Location / Spatial Suggestions
        val zoneLetter = when (item.safeZoneIndex) {
            1 -> "Left (L)"
            2 -> "Middle (M)"
            3 -> "Right (R)"
            else -> "Zone ${item.safeZoneIndex}"
        }
        suggestions.add(
            SmartInventorySuggestion(
                type = SuggestionType.LOCATION,
                title = "Shelf Placement",
                description = "Located on Shelf ${item.safeShelfNumber}, $zoneLetter. Keep active container at front of shelf.",
                priority = 4
            )
        )

        return suggestions.sortedByDescending { it.priority }
    }
}
