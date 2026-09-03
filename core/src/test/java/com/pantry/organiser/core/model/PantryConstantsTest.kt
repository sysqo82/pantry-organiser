package com.pantry.organiser.core.model

import org.junit.Assert.assertEquals
import org.junit.Test

class PantryConstantsTest {

    @Test
    fun `shelfToRow maps correctly`() {
        assertEquals(0, PantryConstants.shelfToRow(4))
        assertEquals(3, PantryConstants.shelfToRow(1))
    }

    @Test
    fun `rowToShelf maps correctly`() {
        assertEquals(4, PantryConstants.rowToShelf(0))
        assertEquals(1, PantryConstants.rowToShelf(3))
    }

    @Test
    fun `isValidBarcodeChecksum validates correctly`() {
        // EAN-13 valid
        assert(PantryConstants.isValidBarcodeChecksum("4006381333931"))
        // EAN-13 invalid
        assert(!PantryConstants.isValidBarcodeChecksum("4006381333932"))
    }
}
