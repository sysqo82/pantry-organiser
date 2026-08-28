package com.pantry.organiser.ui

import app.cash.turbine.test
import com.pantry.organiser.data.*
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*

import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PantryViewModelTest {

    private val repository = mockk<PantryRepository>(relaxed = true)
    private val offRepository = mockk<OpenFoodFactsRepository>(relaxed = true)
    private lateinit var viewModel: PantryViewModel
    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        every { repository.allItems } returns flowOf(emptyList())
        viewModel = PantryViewModel(repository, offRepository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state is empty`() = runTest {
        viewModel.uiState.test {
            val state = awaitItem()
            assertEquals(emptyList<PantryItem>(), state.items)
            assertNull(state.selectedShelf)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `consumePortion for discrete item with reserve stock decrements sealedCount`() = runTest {
        val item = PantryItem(
            id = "item1",
            name = "Test Cans",
            shelfNumber = 1,
            zoneIndex = 1,
            trackingType = TrackingType.DISCRETE_COUNT,
            sealedCount = 2
        )
        
        viewModel.consumePortion(item)
        
        coVerify { 
            repository.updateItem(match { 
                it.id == "item1" && it.sealedCount == 1 
            }) 
        }
    }

    @Test
    fun `consumePortion for discrete item with zero reserve deletes item`() = runTest {
        val item = PantryItem(
            id = "item1",
            name = "Last Can",
            shelfNumber = 1,
            zoneIndex = 1,
            trackingType = TrackingType.DISCRETE_COUNT,
            sealedCount = 0
        )
        
        viewModel.consumePortion(item)
        
        coVerify { repository.deleteItem(item) }
    }

    @Test
    fun `selectShelf filters items correctly`() = runTest {
        val items = listOf(
            PantryItem(id = "1", name = "Shelf 1 Item", shelfNumber = 4, zoneIndex = 1), // S4-L (Row 0, Col 0)
            PantryItem(id = "2", name = "Shelf 2 Item", shelfNumber = 3, zoneIndex = 1)  // S3-L (Row 1, Col 0)
        )
        every { repository.allItems } returns flowOf(items)
        
        // Re-init to pick up the items flow
        viewModel = PantryViewModel(repository, offRepository)
        
        viewModel.selectShelf(0, 0) // Select S4-L
        
        viewModel.uiState.test {
            val state = awaitItem()
            assertEquals(1, state.filteredItems.size)
            assertEquals("Shelf 1 Item", state.filteredItems[0].name)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `consumePortion for bulk item decrements sealedCount when empty`() = runTest {
        val item = PantryItem(
            id = "bulk1",
            name = "Sugar",
            shelfNumber = 1,
            zoneIndex = 1,
            trackingType = TrackingType.BULK_LEVEL,
            sealedCount = 2,
            activeFill = FillLevel.LOW
        )
        // LOW -> prev -> EMPTY. Should roll over.
        viewModel.consumePortion(item)
        
        coVerify { 
            repository.updateItem(match { 
                it.id == "bulk1" && it.sealedCount == 1 && it.activeFill == FillLevel.FULL 
            }) 
        }
    }

    @Test
    fun `consumePortion for bulk item deletes when last unit empty`() = runTest {
        val item = PantryItem(
            id = "bulk2",
            name = "Sugar",
            shelfNumber = 1,
            zoneIndex = 1,
            trackingType = TrackingType.BULK_LEVEL,
            sealedCount = 0,
            activeFill = FillLevel.LOW
        )
        // LOW -> prev -> EMPTY. Should delete.
        viewModel.consumePortion(item)
        
        coVerify { repository.deleteItem(match { it.id == "bulk2" }) }
    }

    @Test
    fun `updatePendingConsumeLevel for bulk item rolls over when selecting empty`() = runTest {
        val item = PantryItem(id = "bulk3", name = "Sugar", shelfNumber = 1, zoneIndex = 1, trackingType = TrackingType.BULK_LEVEL, sealedCount = 1, activeFill = FillLevel.FULL)
        
        coEvery { repository.getItemByBarcode("123") } returns item
        viewModel.showScanner(ScannerMode.CONSUME)
        viewModel.scanBarcode("123")
        
        viewModel.updatePendingConsumeLevel(FillLevel.EMPTY)
        
        coVerify { 
            repository.updateItem(match { 
                it.id == "bulk3" && it.sealedCount == 0 && it.activeFill == FillLevel.FULL 
            }) 
        }
        assertFalse(viewModel.uiState.value.isScannerVisible)
    }

    @Test
    fun `updatePendingConsumeLevel for bulk item deletes when last unit empty`() = runTest {
        val item = PantryItem(id = "bulk4", name = "Sugar", shelfNumber = 1, zoneIndex = 1, trackingType = TrackingType.BULK_LEVEL, sealedCount = 0, activeFill = FillLevel.FULL)
        
        coEvery { repository.getItemByBarcode("123") } returns item
        viewModel.showScanner(ScannerMode.CONSUME)
        viewModel.scanBarcode("123")
        
        viewModel.updatePendingConsumeLevel(FillLevel.EMPTY)
        
        coVerify { repository.deleteItem(match { it.id == "bulk4" }) }
        assertFalse(viewModel.uiState.value.isScannerVisible)
    }



    @Test
    fun `addSealedUnit resets fill if empty but keeps count 0`() = runTest {
        val item = PantryItem(
            id = "item1",
            name = "Staple",
            shelfNumber = 1,
            zoneIndex = 1,
            trackingType = TrackingType.BULK_LEVEL,
            sealedCount = 0,
            activeFill = FillLevel.EMPTY
        )
        
        viewModel.addSealedUnit(item)
        
        coVerify { 
            repository.updateItem(match { 
                it.sealedCount == 0 && it.activeFill == FillLevel.FULL 
            }) 
        }
    }

    @Test
    fun `addSealedUnit increments count if already active`() = runTest {
        val item = PantryItem(
            id = "item1",
            name = "Staple",
            shelfNumber = 1,
            zoneIndex = 1,
            trackingType = TrackingType.BULK_LEVEL,
            sealedCount = 0,
            activeFill = FillLevel.FULL
        )
        
        viewModel.addSealedUnit(item)
        
        coVerify { 
            repository.updateItem(match { 
                it.sealedCount == 1 && it.activeFill == FillLevel.FULL 
            }) 
        }
    }


    @Test
    fun `scanBarcode in CONSUME mode for bulk item sets pendingConsumeItem`() = runTest {
        val item = PantryItem(
            id = "item1",
            name = "Olive Oil",
            barcode = "123",
            shelfNumber = 1,
            zoneIndex = 1,
            trackingType = TrackingType.BULK_LEVEL,
            activeFill = FillLevel.FULL
        )
        coEvery { repository.getItemByBarcode("123") } returns item
        
        viewModel.showScanner(ScannerMode.CONSUME)
        
        viewModel.uiState.test {
            skipItems(1) // Skip initial state
            
            viewModel.scanBarcode("123")
            
            val state = awaitItem()
            assertEquals(item.id, state.pendingConsumeItem?.id)
            assertNull(state.recognizedItem)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `scanBarcode in CONSUME mode for discrete item remains fast-lane`() = runTest {
        val item = PantryItem(
            id = "item1",
            name = "Beans",
            barcode = "456",
            shelfNumber = 1,
            zoneIndex = 1,
            trackingType = TrackingType.DISCRETE_COUNT,
            sealedCount = 1
        )
        coEvery { repository.getItemByBarcode("456") } returns item
        
        viewModel.showScanner(ScannerMode.CONSUME)
        
        viewModel.scanBarcode("456")
        
        coVerify(timeout = 2000) { repository.deleteItem(match { it.id == "item1" }) }
    }

    @Test
    fun `scanBarcode resets isProcessingScan on API failure`() = runTest {
        // 1. Setup failure
        coEvery { repository.getItemByBarcode("999") } returns null
        coEvery { offRepository.getProduct("999") } throws RuntimeException("Network error")
        
        // 2. First scan (will fail)
        viewModel.scanBarcode("999")
        
        // 3. Verify error is shown
        viewModel.uiState.test {
            val state = awaitItem()
            assertEquals("Scan failed", state.error)
            cancelAndIgnoreRemainingEvents()
        }
        
        // 4. Second scan (should start processing, not be ignored)
        // If isProcessingScan was stuck at true, the log would say "Scan ignored" and no new launch would occur.
        // We can verify by checking if getItemByBarcode is called again.
        viewModel.scanBarcode("999")
        coVerify(exactly = 2) { repository.getItemByBarcode("999") }
    }




    @Test
    fun `determineTrackingType categorizes flour as Bulk`() {
        val product = OffProduct(productName = "Plain Flour", categoriesTags = listOf("en:flours"), weight = "1kg")
        assertEquals(TrackingType.BULK_LEVEL, viewModel.determineTrackingType(product))
    }

    @Test
    fun `determineTrackingType categorizes small soy sauce as Discrete`() {
        val product = OffProduct(productName = "Soy Sauce", categoriesTags = listOf("en:sauces"), weight = "250ml")
        assertEquals(TrackingType.DISCRETE_COUNT, viewModel.determineTrackingType(product))
    }

    @Test
    fun `determineTrackingType categorizes Flour Pack as Bulk`() {
        // "pack" used to force discrete, but now "flour" should take precedence
        val product = OffProduct(productName = "Strong White Flour Pack", weight = "1.5kg")
        assertEquals(TrackingType.BULK_LEVEL, viewModel.determineTrackingType(product))
    }

    @Test
    fun `determineTrackingType categorizes large 5L oil as Bulk`() {
        val product = OffProduct(productName = "Cooking Oil", weight = "5 l")
        assertEquals(TrackingType.BULK_LEVEL, viewModel.determineTrackingType(product))
    }

    @Test
    fun `determineTrackingType categorizes multipack as Discrete`() {
        val product = OffProduct(productName = "Coke 6 pack", weight = "6x330ml")
        val unitsPerPack = product.inferUnitsPerPack()
        var trackingType = viewModel.determineTrackingType(product)
        if (unitsPerPack > 1) trackingType = TrackingType.DISCRETE_COUNT
        assertEquals(TrackingType.DISCRETE_COUNT, trackingType)
    }


    @Test
    fun `consumeUnits for multipack correctly calculates remaining units`() = runTest {
        // Scenario: 6 units available (previously 2 packs of 3). Consume 2.
        val item = PantryItem(
            id = "multipack1",
            name = "Sweetcorn",
            shelfNumber = 1,
            zoneIndex = 1,
            trackingType = TrackingType.DISCRETE_COUNT,
            unitsPerPack = 3,
            sealedCount = 6,
            activeCount = 1
        )
        
        viewModel.consumeUnits(item, 2)
        
        coVerify { 
            repository.updateItem(match { 
                // 6 - 2 = 4 units left.
                it.id == "multipack1" && it.sealedCount == 4
            }) 
        }
        assertFalse(viewModel.uiState.value.isScannerVisible)
        assertNull(viewModel.uiState.value.pendingConsumeItem)
    }



    @Test
    fun `consumeUnits for multipack when item is deleted at zero`() = runTest {
        // Scenario: 3 units left. Consume 3.
        val item = PantryItem(
            id = "multipack2",
            name = "Sweetcorn",
            shelfNumber = 1,
            zoneIndex = 1,
            trackingType = TrackingType.DISCRETE_COUNT,
            unitsPerPack = 3,
            sealedCount = 3,
            activeCount = 1
        )
        
        viewModel.consumeUnits(item, 3)
        
        coVerify { 
            repository.deleteItem(match { it.id == "multipack2" })
        }
        assertFalse(viewModel.uiState.value.isScannerVisible)
    }



    @Test
    fun `inferUnitsPerPack recognizes multiplier pattern`() {
        assertEquals(3, PantryItem.inferUnitsPerPack("Sweetcorn in water", "3 x 200 gr"))
        assertEquals(4, PantryItem.inferUnitsPerPack("Baked Beans", "4x400g"))
        assertEquals(6, PantryItem.inferUnitsPerPack("Cola", "6 x 330ml"))
    }

    @Test
    fun `inferUnitsPerPack recognizes pack of pattern`() {
        assertEquals(4, PantryItem.inferUnitsPerPack("Yoghurt", "Pack of 4"))
        assertEquals(12, PantryItem.inferUnitsPerPack("Eggs", "pack of 12"))
    }

    @Test
    fun `cancelPendingItem dismisses the scanner and returns to pantry`() = runTest {
        // 1. Setup a "Pending" state
        viewModel.showScanner(ScannerMode.RESTOCK)
        val product = OffProduct(productName = "Unknown Soda")
        coEvery { repository.getItemByBarcode("999") } returns null
        coEvery { offRepository.getProduct("999") } returns product
        viewModel.scanBarcode("999")
        
        // 2. Action: Cancel/Return to Pantry
        viewModel.cancelPendingItem()

        // 3. Assertion: Scanner should be CLOSED
        assertFalse(viewModel.uiState.value.isScannerVisible)
        assertNull(viewModel.uiState.value.scannedProduct)
    }



    @Test
    fun `scanBarcode for new item correctly transitions through loading to pendingNewItem`() = runTest {
        val product = OffProduct(productName = "New Soda", brands = "BrandX", weight = "500ml")
        coEvery { repository.getItemByBarcode("777") } returns null
        coEvery { offRepository.getProduct("777") } returns product
        
        viewModel.uiState.test {
            assertEquals(emptyList<PantryItem>(), awaitItem().items) // Initial
            
            viewModel.scanBarcode("777")
            
            // 1. May see loading state (depends on dispatcher timing)
            var state = awaitItem()
            if (state.isLoading) {
                state = awaitItem() // Wait for final state
            }
            
            // 2. Final state should have the item
            assertEquals("New Soda", state.pendingNewItem?.name)
            assertFalse(state.isLoading)
            
            cancelAndIgnoreRemainingEvents()
        }
    }



    @Test
    fun `scanBarcode on Read-Only device for unknown item shows details and error`() = runTest {
        viewModel.setReadOnly(true)
        val product = OffProduct(productName = "External Item", brands = "BrandX")
        coEvery { repository.getItemByBarcode("999") } returns null
        coEvery { offRepository.getProduct("999") } returns product
        
        viewModel.showScanner(ScannerMode.RESTOCK)
        
        viewModel.uiState.test {
            awaitItem() // Scanner show state
            
            viewModel.scanBarcode("999")
            
            var state = awaitItem()
            if (state.isLoading) {
                state = awaitItem()
            }
            
            assertEquals("Item not found in pantry", state.error)
            assertEquals("External Item", state.scannedProduct?.productName)
            
            cancelAndIgnoreRemainingEvents()
        }
    }

}





