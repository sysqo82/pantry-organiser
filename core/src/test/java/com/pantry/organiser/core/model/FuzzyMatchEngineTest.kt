package com.pantry.organiser.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FuzzyMatchEngineTest {

    private val pantryItems = listOf(
        PantryItem(
            id = "item1",
            name = "Sainsbury's Granulated White Sugar",
            brand = "Sainsbury's",
            packageQuantity = "1kg",
            shelfNumber = 2,
            zoneIndex = 2,
            barcode = "5012345678901",
            isAssigned = true
        ),
        PantryItem(
            id = "item2",
            name = "Tesco Baked Beans in Tomato Sauce",
            brand = "Tesco",
            packageQuantity = "415g",
            shelfNumber = 1,
            zoneIndex = 1,
            barcode = "5000123456789",
            isAssigned = true
        ),
        PantryItem(
            id = "item3",
            name = "Anchor Unsalted Butter",
            brand = "Anchor",
            packageQuantity = "250g",
            shelfNumber = 3,
            zoneIndex = 1,
            barcode = "5011122334455",
            isAssigned = true
        ),
        PantryItem(
            id = "item4",
            name = "Cravendale Whole Milk",
            brand = "Cravendale",
            packageQuantity = "2L",
            shelfNumber = 4,
            zoneIndex = 3,
            barcode = "5099988776655",
            isAssigned = true
        )
    )

    @Test
    fun `findBestMatch matches sugar across different brands`() {
        val result = FuzzyMatchEngine.findBestMatch(
            scannedProductName = "Aldi Granulated Sugar 1kg",
            scannedBrand = "Aldi",
            scannedBarcode = "4088123456789",
            pantryItems = pantryItems
        )

        assertNotNull(result)
        assertEquals("item1", result?.matchedItem?.id)
        assertEquals("Sainsbury's Granulated White Sugar", result?.matchedItem?.name)
        assertTrue("Score should be above threshold", (result?.score ?: 0.0) >= 0.55)
    }

    @Test
    fun `findBestMatch matches baked beans across different brands`() {
        val result = FuzzyMatchEngine.findBestMatch(
            scannedProductName = "Heinz Baked Beans in Tomato Sauce 415g",
            scannedBrand = "Heinz",
            scannedBarcode = "5000000111222",
            pantryItems = pantryItems
        )

        assertNotNull(result)
        assertEquals("item2", result?.matchedItem?.id)
        assertEquals("Tesco Baked Beans in Tomato Sauce", result?.matchedItem?.name)
    }

    @Test
    fun `findBestMatch respects qualifier conflicts like salted vs unsalted butter`() {
        val result = FuzzyMatchEngine.findBestMatch(
            scannedProductName = "Lurpak Salted Butter 250g",
            scannedBrand = "Lurpak",
            scannedBarcode = "5700000111222",
            pantryItems = pantryItems
        )

        // Item 3 in pantry is Anchor UNSALTED Butter -> Conflict!
        assertNull(result)
    }

    @Test
    fun `findBestMatch ignores item with exact same barcode`() {
        val result = FuzzyMatchEngine.findBestMatch(
            scannedProductName = "Sainsbury's Granulated White Sugar",
            scannedBrand = "Sainsbury's",
            scannedBarcode = "5012345678901", // Exact match with item1
            pantryItems = pantryItems
        )

        // Exact matches are skipped by fuzzy match engine because exact scan handles it directly
        assertNull(result)
    }

    @Test
    fun `findBestMatch returns null when no similar product type exists`() {
        val result = FuzzyMatchEngine.findBestMatch(
            scannedProductName = "Basmati Rice 1kg",
            scannedBrand = "Tilda",
            scannedBarcode = "5012345000000",
            pantryItems = pantryItems
        )

        assertNull(result)
    }

    @Test
    fun `findAllMatches returns all matching items when multiple fit pattern`() {
        val multipleItems = listOf(
            PantryItem(
                id = "cocoa1",
                name = "Tesco Cocoa Powder",
                brand = "Tesco",
                shelfNumber = 1,
                zoneIndex = 1,
                barcode = "50001",
                isAssigned = true
            ),
            PantryItem(
                id = "cocoa2",
                name = "Bournville Cocoa Powder",
                brand = "Bournville",
                shelfNumber = 2,
                zoneIndex = 3,
                barcode = "50002",
                isAssigned = true
            )
        )

        val matches = FuzzyMatchEngine.findAllMatches(
            scannedProductName = "Cadbury Cocoa Powder 250g",
            scannedBrand = "Cadbury",
            scannedBarcode = "50003",
            pantryItems = multipleItems
        )

        assertEquals(2, matches.size)
        assertTrue(matches.any { it.matchedItem.id == "cocoa1" })
        assertTrue(matches.any { it.matchedItem.id == "cocoa2" })
    }
}
