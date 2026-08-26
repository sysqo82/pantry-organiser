package com.pantry.organiser.ui

import app.cash.turbine.test
import com.pantry.organiser.data.*
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
    fun `addSealedUnit increments count and resets fill if empty`() = runTest {
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
        
        coVerify(timeout = 2000) { repository.updateItem(match { it.sealedCount == 0 }) }
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
        assertEquals(TrackingType.DISCRETE_COUNT, viewModel.determineTrackingType(product))
    }
}
