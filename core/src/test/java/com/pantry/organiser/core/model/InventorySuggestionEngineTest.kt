package com.pantry.organiser.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InventorySuggestionEngineTest {

    @Test
    fun `generateSuggestions returns low active fill warning when bulk level is low`() {
        val item = PantryItem(
            id = "item_1",
            name = "Plain Flour",
            shelfNumber = 2,
            zoneIndex = 2,
            trackingType = TrackingType.BULK_LEVEL,
            sealedCount = 1,
            activeFill = FillLevel.LOW
        )

        val suggestions = InventorySuggestionEngine.generateSuggestions(item)

        assertTrue(suggestions.any { it.title == "Active Pack Running Low" })
        assertEquals("Active Pack Running Low", suggestions.first().title)
    }

    @Test
    fun `generateSuggestions returns out of stock suggestion when total display count is zero`() {
        val item = PantryItem(
            id = "item_2",
            name = "Canned Tomatoes",
            shelfNumber = 1,
            zoneIndex = 1,
            trackingType = TrackingType.DISCRETE_COUNT,
            sealedCount = 0,
            activeCount = 0
        )

        val suggestions = InventorySuggestionEngine.generateSuggestions(item)

        assertTrue(suggestions.any { it.title == "Out of Stock" })
        assertEquals("Out of Stock", suggestions.first().title)
    }

    @Test
    fun `generateSuggestions includes shelf location suggestion`() {
        val item = PantryItem(
            id = "item_3",
            name = "Basmati Rice",
            shelfNumber = 3,
            zoneIndex = 3,
            trackingType = TrackingType.BULK_LEVEL,
            sealedCount = 2,
            activeFill = FillLevel.FULL
        )

        val suggestions = InventorySuggestionEngine.generateSuggestions(item)

        val locationSuggestion = suggestions.find { it.type == SuggestionType.LOCATION }
        assertTrue(locationSuggestion != null)
        assertTrue(locationSuggestion!!.description.contains("Shelf 3, Right (R)"))
    }
}
