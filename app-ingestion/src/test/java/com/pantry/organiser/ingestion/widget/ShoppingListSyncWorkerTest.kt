package com.pantry.organiser.ingestion.widget

import com.pantry.organiser.core.model.BatchPayload
import com.pantry.organiser.core.model.PantryItem
import com.pantry.organiser.core.model.PastItem
import com.pantry.organiser.core.model.ScannedItem
import com.pantry.organiser.core.network.SyncService
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class ShoppingListSyncWorkerTest {

    private class FakeSyncService : SyncService {
        val dispatchedBatches = mutableListOf<BatchPayload>()
        val deletedPastItemIds = mutableListOf<String>()
        val pastItems = mutableListOf(
            PastItem(id = "past_1", name = "Milk", barcode = "123", brand = "Dairy"),
            PastItem(id = "past_2", name = "Bread", barcode = "456", packageQuantity = "500g")
        )

        override suspend fun dispatchBatch(payload: BatchPayload) {
            dispatchedBatches.add(payload)
        }
        override fun observeBatches(pantryId: String): Flow<BatchPayload> = emptyFlow()
        override suspend fun fetchBatches(pantryId: String): List<BatchPayload> = emptyList()

        override suspend fun fetchPantryItems(pantryId: String): List<PantryItem> = emptyList()
        override suspend fun fetchPantryItemById(itemId: String): PantryItem? = null
        override fun observePantryItems(pantryId: String): Flow<PantryItem> = emptyFlow()
        override suspend fun createPantryItem(item: PantryItem): PantryItem? = null
        override suspend fun updatePantryItem(item: PantryItem): PantryItem? = null
        override suspend fun deletePantryItem(itemId: String): Boolean = true
        override suspend fun uploadPantryItemImage(itemId: String, imageBytes: ByteArray, filename: String): PantryItem? = null

        override suspend fun fetchPastItems(pantryId: String): List<PastItem> = pastItems
        override fun observePastItems(pantryId: String): Flow<PastItem> = emptyFlow()
        override suspend fun createPastItem(item: PastItem): PastItem? = null
        override suspend fun deletePastItem(itemId: String): Boolean {
            deletedPastItemIds.add(itemId)
            pastItems.removeAll { it.id == itemId }
            return true
        }
    }

    @Test
    fun `processPastItemsForWidget deduplicates items by barcode`() {
        val item1 = PastItem(id = "1", name = "Milk", barcode = "123456", brand = "Brand A")
        val item2 = PastItem(id = "2", name = "Milk Whole", barcode = "123456", brand = "Brand A")
        val item3 = PastItem(id = "3", name = "Bread", barcode = "654321", brand = "Brand B")

        val result = ShoppingListWidgetSyncManager.processPastItemsForWidget(listOf(item1, item2, item3))

        assertEquals(2, result.size)
        val barcodes = result.map { it.barcode }
        assertEquals(listOf("654321", "123456"), barcodes)
    }

    @Test
    fun `processPastItemsForWidget deduplicates items by name when barcode is null or blank`() {
        val item1 = PastItem(id = "1", name = "Apples", barcode = null)
        val item2 = PastItem(id = "2", name = "apples", barcode = "")
        val item3 = PastItem(id = "3", name = "Bananas", barcode = null)

        val result = ShoppingListWidgetSyncManager.processPastItemsForWidget(listOf(item1, item2, item3))

        assertEquals(2, result.size)
        assertEquals(listOf("Apples", "Bananas"), result.map { it.name })
    }

    @Test
    fun `processPastItemsForWidget sorts items alphabetically by name`() {
        val item1 = PastItem(id = "1", name = "Zucchini", barcode = "111")
        val item2 = PastItem(id = "2", name = "Apples", barcode = "222")
        val item3 = PastItem(id = "3", name = "Milk", barcode = "333")

        val result = ShoppingListWidgetSyncManager.processPastItemsForWidget(listOf(item1, item2, item3))

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

        val result = ShoppingListWidgetSyncManager.processPastItemsForWidget(listOf(pastItem))

        assertEquals(1, result.size)
        val widgetItem = result[0]
        assertEquals("past_1", widgetItem.id)
        assertEquals("Organic Milk", widgetItem.name)
        assertEquals("Farm Fresh", widgetItem.brand)
        assertEquals("1L", widgetItem.packageQuantity)
        assertEquals("987654321", widgetItem.barcode)
    }

    @Test
    fun `ShoppingWidgetStateSerializer serializes and deserializes Success state correctly`() = runBlocking {
        val state = ShoppingWidgetState.Success(
            items = listOf(
                WidgetShoppingItem(id = "1", name = "Apples", brand = "Orchard"),
                WidgetShoppingItem(id = "2", name = "Butter", packageQuantity = "250g")
            ),
            expandedItemId = "1",
            statusMessage = "Added Apples to Pantry",
            lastUpdatedMillis = 1700000000000L
        )

        val output = ByteArrayOutputStream()
        ShoppingWidgetStateSerializer.writeTo(state, output)

        val input = ByteArrayInputStream(output.toByteArray())
        val readState = ShoppingWidgetStateSerializer.readFrom(input)

        assertTrue(readState is ShoppingWidgetState.Success)
        val successState = readState as ShoppingWidgetState.Success
        assertEquals(2, successState.items.size)
        assertEquals("1", successState.expandedItemId)
        assertEquals("Added Apples to Pantry", successState.statusMessage)
        assertEquals(1700000000000L, successState.lastUpdatedMillis)
    }

    @Test
    fun `ShoppingWidgetStateSerializer returns Loading on empty input`() = runBlocking {
        val input = ByteArrayInputStream(ByteArray(0))
        val readState = ShoppingWidgetStateSerializer.readFrom(input)

        assertTrue(readState is ShoppingWidgetState.Loading)
    }

    @Test
    fun `markItemPurchased dispatches batch payload to intake and deletes past item`() = runBlocking {
        val fakeSync = FakeSyncService()

        val widgetItem = WidgetShoppingItem(
            id = "past_1",
            name = "Milk",
            brand = "Dairy",
            barcode = "123"
        )

        fakeSync.dispatchBatch(
            BatchPayload(
                pantryId = "default-pantry",
                items = listOf(
                    ScannedItem(
                        barcode = widgetItem.barcode ?: "",
                        productName = widgetItem.name,
                        brand = widgetItem.brand ?: ""
                    )
                )
            )
        )
        fakeSync.deletePastItem(widgetItem.id)

        assertEquals(1, fakeSync.dispatchedBatches.size)
        val batch = fakeSync.dispatchedBatches[0]
        assertEquals(1, batch.items.size)
        assertEquals("Milk", batch.items[0].productName)
        assertEquals("Dairy", batch.items[0].brand)
        assertEquals("123", batch.items[0].barcode)
        assertEquals(listOf("past_1"), fakeSync.deletedPastItemIds)

        val pastItemsAfter = fakeSync.fetchPastItems("default-pantry")
        assertEquals(1, pastItemsAfter.size)
        assertEquals("Bread", pastItemsAfter[0].name)
    }
}
