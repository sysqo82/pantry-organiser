package com.pantry.organiser.core.model

import org.junit.Assert.assertEquals
import org.junit.Test

class PantryItemTest {

    @Test
    fun `determineTrackingType categorizes multipack tins as discrete`() {
        val type = PantryItem.determineTrackingType(
            name = "Heinz Baked Beans 4x400g",
            quantity = "4x400g",
            unitsPerPack = 4
        )
        assertEquals(TrackingType.DISCRETE_COUNT, type)
    }

    @Test
    fun `determineTrackingType categorizes sugar, flour, pasta as bulk level`() {
        val sugar = PantryItem.determineTrackingType(name = "Silver Spoon Granulated Sugar", quantity = "1kg")
        val flour = PantryItem.determineTrackingType(name = "Homepride Plain Flour", quantity = "1.5kg")
        val pasta = PantryItem.determineTrackingType(name = "Napolina Spaghetti", quantity = "500g")
        val bucatini = PantryItem.determineTrackingType(name = "N-Bucatini n°9", quantity = "500g")
        val penne = PantryItem.determineTrackingType(name = "Barilla Penne Rigate", quantity = "500g")
        val fusilli = PantryItem.determineTrackingType(name = "Fusilli", quantity = "500g")
        val rice = PantryItem.determineTrackingType(name = "Tilda Basmati Rice", quantity = "1kg")

        assertEquals(TrackingType.BULK_LEVEL, sugar)
        assertEquals(TrackingType.BULK_LEVEL, flour)
        assertEquals(TrackingType.BULK_LEVEL, pasta)
        assertEquals(TrackingType.BULK_LEVEL, bucatini)
        assertEquals(TrackingType.BULK_LEVEL, penne)
        assertEquals(TrackingType.BULK_LEVEL, fusilli)
        assertEquals(TrackingType.BULK_LEVEL, rice)
    }

    @Test
    fun `determineTrackingType categorizes sauce bottles as discrete`() {
        val ketchup = PantryItem.determineTrackingType(name = "Heinz Tomato Ketchup", quantity = "500ml")
        val soySauce = PantryItem.determineTrackingType(name = "Lee Kum Kee Soy Sauce", quantity = "150ml")
        val mayo = PantryItem.determineTrackingType(name = "Hellmann's Real Mayonnaise", quantity = "430ml")

        assertEquals(TrackingType.DISCRETE_COUNT, ketchup)
        assertEquals(TrackingType.DISCRETE_COUNT, soySauce)
        assertEquals(TrackingType.DISCRETE_COUNT, mayo)
    }

    @Test
    fun `determineTrackingType categorizes tins and cans as discrete`() {
        val soup = PantryItem.determineTrackingType(name = "Heinz Tomato Soup", quantity = "400g")
        val tuna = PantryItem.determineTrackingType(name = "John West Tuna Steak in Spring Water", quantity = "145g")

        assertEquals(TrackingType.DISCRETE_COUNT, soup)
        assertEquals(TrackingType.DISCRETE_COUNT, tuna)
    }

    @Test
    fun `determineTrackingType categorizes pearl couscous as discrete`() {
        val pearlCouscous = PantryItem.determineTrackingType(name = "Tesco Pearl Couscous", quantity = "500g")
        val giantCouscous = PantryItem.determineTrackingType(name = "Sainsbury's Giant Couscous", quantity = "300g")
        val israeliCouscous = PantryItem.determineTrackingType(name = "Merchant Gourmet Israeli Couscous", quantity = "250g")

        assertEquals(TrackingType.DISCRETE_COUNT, pearlCouscous)
        assertEquals(TrackingType.DISCRETE_COUNT, giantCouscous)
        assertEquals(TrackingType.DISCRETE_COUNT, israeliCouscous)
    }

    @Test
    fun `conversion between PantryItem and PastItem preserves all fields`() {
        val originalPantryItem = PantryItem(
            id = "item_123",
            name = "Kikkoman Soy Sauce",
            barcode = "5012345678901",
            brand = "Kikkoman",
            packageQuantity = "250ml",
            imageUrl = "http://example.com/custom.jpg",
            apiImageUrl = "http://example.com/api.jpg",
            localImageUrl = "http://example.com/local.jpg",
            localImageUri = "content://media/123",
            shelfNumber = 4,
            zoneIndex = 3,
            trackingType = TrackingType.DISCRETE_COUNT,
            sealedCount = 2,
            unitsPerPack = 1,
            activeCount = 1,
            activeFill = FillLevel.FULL,
            isAssigned = true,
            createdAt = 1000L,
            updatedAt = 2000L
        )

        val pastItem = originalPantryItem.toPastItem()
        assertEquals(originalPantryItem.id, pastItem.id)
        assertEquals(originalPantryItem.name, pastItem.name)
        assertEquals(originalPantryItem.barcode, pastItem.barcode)
        assertEquals(originalPantryItem.brand, pastItem.brand)
        assertEquals(originalPantryItem.packageQuantity, pastItem.packageQuantity)
        assertEquals(originalPantryItem.imageUrl, pastItem.imageUrl)
        assertEquals(originalPantryItem.apiImageUrl, pastItem.apiImageUrl)
        assertEquals(originalPantryItem.localImageUrl, pastItem.localImageUrl)
        assertEquals(originalPantryItem.localImageUri, pastItem.localImageUri)
        assertEquals(originalPantryItem.shelfNumber, pastItem.shelfNumber)
        assertEquals(originalPantryItem.zoneIndex, pastItem.zoneIndex)
        assertEquals(originalPantryItem.trackingType, pastItem.trackingType)
        assertEquals(originalPantryItem.sealedCount, pastItem.sealedCount)

        val restoredPantryItem = pastItem.toPantryItem()
        assertEquals(originalPantryItem, restoredPantryItem)
    }
}
