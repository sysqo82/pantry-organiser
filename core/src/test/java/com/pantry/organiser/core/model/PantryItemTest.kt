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
}
