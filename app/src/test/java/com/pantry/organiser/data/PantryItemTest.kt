package com.pantry.organiser.data

import org.junit.Assert.assertEquals
import org.junit.Test

class PantryItemTest {

    @Test
    fun `totalDisplayCount for discrete items returns sealedCount`() {
        val item = PantryItem(
            id = "1",
            name = "Coke",
            trackingType = TrackingType.DISCRETE_COUNT,
            sealedCount = 5,
            unitsPerPack = 1,
            shelfNumber = 1,
            zoneIndex = 1
        )
        assertEquals(5, item.totalDisplayCount)
    }

    @Test
    fun `totalDisplayCount for bulk items adds 1 if not empty`() {
        val itemFull = PantryItem(
            id = "1",
            name = "Sugar",
            trackingType = TrackingType.BULK_LEVEL,
            sealedCount = 1,
            activeFill = FillLevel.FULL,
            shelfNumber = 1,
            zoneIndex = 1
        )
        assertEquals(2, itemFull.totalDisplayCount)

        val itemEmpty = PantryItem(
            id = "2",
            name = "Flour",
            trackingType = TrackingType.BULK_LEVEL,
            sealedCount = 1,
            activeFill = FillLevel.EMPTY,
            shelfNumber = 1,
            zoneIndex = 1
        )
        assertEquals(1, itemEmpty.totalDisplayCount)
    }

    @Test
    fun `getDisplayUnitLabel returns correct labels`() {
        val discrete = PantryItem(
            id = "1",
            name = "Coke",
            trackingType = TrackingType.DISCRETE_COUNT,
            sealedCount = 2,
            shelfNumber = 1,
            zoneIndex = 1
        )
        assertEquals("Units", discrete.getDisplayUnitLabel())
        assertEquals("Unit", discrete.getDisplayUnitLabel(isPlural = false))

        val bulk = PantryItem(
            id = "2",
            name = "Sugar",
            trackingType = TrackingType.BULK_LEVEL,
            sealedCount = 1,
            shelfNumber = 1,
            zoneIndex = 1
        )
        assertEquals("Portions", bulk.getDisplayUnitLabel())
        assertEquals("Portion", bulk.getDisplayUnitLabel(isPlural = false))

        val multipack = PantryItem(
            id = "3",
            name = "Sweetcorn",
            trackingType = TrackingType.DISCRETE_COUNT,
            unitsPerPack = 3,
            sealedCount = 6,
            shelfNumber = 1,
            zoneIndex = 1
        )
        assertEquals("Cans/Tins", multipack.getDisplayUnitLabel())
        assertEquals("Can/Tin", multipack.getDisplayUnitLabel(isPlural = false))
    }
}
