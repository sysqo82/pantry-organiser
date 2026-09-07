package com.pantry.organiser.ingestion.widget

import com.pantry.organiser.core.model.PastItem
import org.junit.Assert.assertEquals
import org.junit.Test

class ShoppingListSyncWorkerTest {

    @Test
    fun `processPastItemsForWidget deduplicates items by barcode`() {
        val item1 = PastItem(id = "1", name = "Milk", barcode = "123456", brand = "Brand A")
        val item2 = PastItem(id = "2", name = "Milk Whole", barcode = "123456", brand = "Brand A")
        val item3 = PastItem(id = "3", name = "Bread", barcode = "654321", brand = "Brand B")

        val result = ShoppingListSyncWorker.processPastItemsForWidget(listOf(item1, item2, item3))

        assertEquals(2, result.size)
        val barcodes = result.map { it.barcode }
        assertEquals(listOf("654321", "123456"), barcodes)
    }

    @Test
    fun `processPastItemsForWidget deduplicates items by name when barcode is null or blank`() {
        val item1 = PastItem(id = "1", name = "Apples", barcode = null)
        val item2 = PastItem(id = "2", name = "apples", barcode = "")
        val item3 = PastItem(id = "3", name = "Bananas", barcode = null)

        val result = ShoppingListSyncWorker.processPastItemsForWidget(listOf(item1, item2, item3))

        assertEquals(2, result.size)
        assertEquals(listOf("Apples", "Bananas"), result.map { it.name })
    }

    @Test
    fun `processPastItemsForWidget sorts items alphabetically by name`() {
        val item1 = PastItem(id = "1", name = "Zucchini", barcode = "111")
        val item2 = PastItem(id = "2", name = "Apples", barcode = "222")
        val item3 = PastItem(id = "3", name = "Milk", barcode = "333")

        val result = ShoppingListSyncWorker.processPastItemsForWidget(listOf(item1, item2, item3))

        val names = result.map { it.name }
        assertEquals(listOf("Apples", "Milk", "Zucchini"), names)
    }

    @Test
    fun `processPastItemsForWidget maps fields correctly`() {
        val pastItem = PastItem(
            id = "past_1",
            name = "Organic Milk",
            brand = "Farm Fresh",
            packageQuantity = "1L",
            barcode = "987654321"
        )

        val result = ShoppingListSyncWorker.processPastItemsForWidget(listOf(pastItem))

        assertEquals(1, result.size)
        val widgetItem = result[0]
        assertEquals("past_1", widgetItem.id)
        assertEquals("Organic Milk", widgetItem.name)
        assertEquals("Farm Fresh", widgetItem.brand)
        assertEquals("1L", widgetItem.packageQuantity)
        assertEquals("987654321", widgetItem.barcode)
    }
}
