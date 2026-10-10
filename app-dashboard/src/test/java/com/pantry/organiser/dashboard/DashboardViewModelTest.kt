package com.pantry.organiser.dashboard

import com.pantry.organiser.core.model.FillLevel
import com.pantry.organiser.core.model.PantryItem
import com.pantry.organiser.core.model.PastItem
import com.pantry.organiser.core.model.TrackingType
import com.pantry.organiser.dashboard.data.OpenFoodFactsProber
import com.pantry.organiser.dashboard.data.PantryRepository
import com.pantry.organiser.dashboard.data.SyncQueueItem
import com.pantry.organiser.dashboard.data.SyncQueueRepository
import com.pantry.organiser.dashboard.ui.OverlayContext
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DashboardViewModelTest {

    private val syncQueueRepository = mockk<SyncQueueRepository>(relaxed = true)
    private val pantryRepository = mockk<PantryRepository>(relaxed = true)
    private val openFoodFactsProber = mockk<OpenFoodFactsProber>(relaxed = true)
    private lateinit var viewModel: DashboardViewModel
    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        mockkStatic(android.util.Log::class)
        every { android.util.Log.d(any(), any()) } returns 0
        every { android.util.Log.e(any(), any(), any()) } returns 0
        every { syncQueueRepository.getPendingItems() } returns flowOf(emptyList())
        every { pantryRepository.allItems } returns flowOf(emptyList())
        every { pantryRepository.pastItems } returns flowOf(emptyList())
        viewModel = DashboardViewModel(syncQueueRepository, pantryRepository, openFoodFactsProber)
    }

    @After
    fun tearDown() {
        clearAllMocks()
        unmockkStatic(android.util.Log::class)
        Dispatchers.resetMain()
    }

    @Test
    fun `saveEnrichedItem saves bulk staple item like salt with active stock and reserve count`() = runTest {
        val syncItem = SyncQueueItem(
            id = "batch1_item1",
            barcode = "5000462326897",
            scannedAt = 1000L,
            batchId = "batch1",
            productName = "British Cooking Salt",
            brand = "Tesco",
            imageUrl = "https://images.openfoodfacts.org/salt.jpg",
            quantity = "750g"
        )

        val slot = slot<PantryItem>()
        coEvery { pantryRepository.addItem(capture(slot)) } returns Unit
        coEvery { syncQueueRepository.markAsProcessed("batch1_item1") } returns Unit

        viewModel.saveEnrichedItem(
            syncItem = syncItem,
            existingItem = null,
            shelf = 1,
            zone = 1,
            quantityToAdd = 2,
            fillLevel = FillLevel.FULL
        )

        coVerify(exactly = 1) { pantryRepository.addItem(any()) }
        coVerify(exactly = 1) { syncQueueRepository.markAsProcessed("batch1_item1") }

        val captured = slot.captured
        assertEquals("British Cooking Salt", captured.name)
        assertEquals(TrackingType.BULK_LEVEL, captured.trackingType)
        assertEquals(1, captured.shelfNumber)
        assertEquals(1, captured.zoneIndex)
        assertEquals(FillLevel.FULL, captured.activeFill)
        assertEquals(1, captured.sealedCount)
    }

    @Test
    fun `pantryItems in uiState are deterministically sorted by spatial location`() = runTest {
        val itemS1 = PantryItem(id = "1", name = "Salt", shelfNumber = 1, zoneIndex = 1, isAssigned = true, activeCount = 1)
        val itemS4R = PantryItem(id = "2", name = "Soy Sauce", shelfNumber = 4, zoneIndex = 3, isAssigned = true, activeCount = 2)
        val itemS4M = PantryItem(id = "3", name = "Pizza Topper", shelfNumber = 4, zoneIndex = 2, isAssigned = true, activeCount = 1)

        val unsortedItems = listOf(itemS1, itemS4R, itemS4M)
        every { pantryRepository.allItems } returns flowOf(unsortedItems)

        val vm = DashboardViewModel(syncQueueRepository, pantryRepository, openFoodFactsProber)
        val stateItems = vm.uiState.value.pantryItems

        // Expected spatial order: S1-L (shelf 1, zone 1), S4-M (shelf 4, zone 2), S4-R (shelf 4, zone 3)
        assertEquals(3, stateItems.size)
        assertEquals("Salt", stateItems[0].name)
        assertEquals("Pizza Topper", stateItems[1].name)
        assertEquals("Soy Sauce", stateItems[2].name)
    }

    @Test
    fun `openEnrichmentModal merges into existing assigned item with same barcode`() = runTest {
        val assignedItem = PantryItem(
            id = "assigned_1",
            name = "Pizza Topper",
            barcode = "12345",
            shelfNumber = 3,
            zoneIndex = 2,
            trackingType = TrackingType.DISCRETE_COUNT,
            unitsPerPack = 1,
            activeCount = 1,
            sealedCount = 0,
            isAssigned = true
        )

        val unassignedItem = PantryItem(
            id = "unassigned_2",
            name = "Pizza Topper",
            barcode = "12345",
            shelfNumber = 1,
            zoneIndex = 1,
            trackingType = TrackingType.DISCRETE_COUNT,
            unitsPerPack = 1,
            activeCount = 1,
            sealedCount = 1,
            isAssigned = false
        )

        val syncItem = SyncQueueItem(
            id = "unassigned_unassigned_2",
            itemId = "unassigned_2",
            barcode = "12345",
            scannedAt = 1000L,
            batchId = "batch1",
            productName = "Pizza Topper",
            brand = "Tesco",
            imageUrl = "",
            quantity = "1 Unit"
        )

        every { pantryRepository.allItems } returns flowOf(listOf(assignedItem, unassignedItem))
        coEvery { pantryRepository.getItemByBarcode("12345") } returns assignedItem

        val vm = DashboardViewModel(syncQueueRepository, pantryRepository, openFoodFactsProber)

        vm.openEnrichmentModal(syncItem)

        val overlay = vm.uiState.value.activeOverlay
        assertTrue(overlay is OverlayContext.SyncQueueEnrichment)
        val enrichment = overlay as OverlayContext.SyncQueueEnrichment
        assertEquals("assigned_1", enrichment.existingItem?.id)
    }

    @Test
    fun `processItem for existing assigned item automatically restocks to current shelf without showing overlay`() = runTest {
        val assignedItem = PantryItem(
            id = "assigned_1",
            name = "Pizza Topper",
            barcode = "12345",
            shelfNumber = 3,
            zoneIndex = 2,
            trackingType = TrackingType.DISCRETE_COUNT,
            unitsPerPack = 1,
            activeCount = 1,
            sealedCount = 0,
            isAssigned = true
        )

        val syncItem = SyncQueueItem(
            id = "sq_12345",
            itemId = "assigned_1",
            barcode = "12345",
            scannedAt = 1000L,
            batchId = "batch1",
            productName = "Pizza Topper"
        )

        every { pantryRepository.allItems } returns flowOf(listOf(assignedItem))
        coEvery { pantryRepository.getItemByBarcode("12345") } returns assignedItem

        val updateSlot = slot<PantryItem>()
        coEvery { pantryRepository.updateItem(capture(updateSlot)) } returns Unit
        coEvery { syncQueueRepository.markAsProcessed("sq_12345") } returns Unit

        val vm = DashboardViewModel(syncQueueRepository, pantryRepository, openFoodFactsProber)
        vm.processItem(syncItem)

        assertNull(vm.uiState.value.activeOverlay) // Overlay remains null (1-Tap auto restocked!)
        coVerify(exactly = 1) { pantryRepository.updateItem(any()) }
        coVerify(exactly = 1) { syncQueueRepository.markAsProcessed("sq_12345") }

        val updated = updateSlot.captured
        assertEquals("assigned_1", updated.id)
        assertEquals(3, updated.shelfNumber)
        assertEquals(2, updated.zoneIndex)
    }

    @Test
    fun `probeOpenFoodFacts triggers prober and updates probeMessage in state`() = runTest {
        coEvery { openFoodFactsProber.probeAndSync() } returns 2

        viewModel.probeOpenFoodFacts()

        coVerify(exactly = 1) { openFoodFactsProber.probeAndSync() }
        assertEquals("Probed Open Food Facts: 2 item(s) updated.", viewModel.uiState.value.probeMessage)
        assertFalse(viewModel.uiState.value.isProbing)

        viewModel.clearProbeMessage()
        assertNull(viewModel.uiState.value.probeMessage)
    }

    @Test
    fun `pendingItems in uiState only contains real pending items and does not synthesize items from pantry`() = runTest {
        val item1 = PantryItem(id = "1", name = "Salt", shelfNumber = 1, zoneIndex = 1, isAssigned = false, activeCount = 1)
        val item2 = PantryItem(id = "2", name = "Pepper", shelfNumber = 1, zoneIndex = 2, isAssigned = false, activeCount = 1)

        every { syncQueueRepository.getPendingItems() } returns flowOf(emptyList())
        every { pantryRepository.allItems } returns flowOf(listOf(item1, item2))

        val vm = DashboardViewModel(syncQueueRepository, pantryRepository, openFoodFactsProber)

        assertEquals(0, vm.uiState.value.pendingItems.size)
        assertEquals(2, vm.uiState.value.pantryItems.size)
    }

    @Test
    fun `clearAllPendingItems clears sync queue without deleting pantry items`() = runTest {
        val pendingItem = SyncQueueItem(
            id = "sq_1",
            itemId = "pantry_item_123",
            barcode = "12345678",
            scannedAt = 1000L,
            batchId = "batch1",
            productName = "Rice"
        )

        every { syncQueueRepository.getPendingItems() } returns flowOf(listOf(pendingItem))
        val vm = DashboardViewModel(syncQueueRepository, pantryRepository, openFoodFactsProber)

        vm.clearAllPendingItems()

        coVerify(exactly = 1) { syncQueueRepository.clearAllPendingItems() }
        coVerify(exactly = 0) { pantryRepository.deleteItem(any()) }
    }

    @Test
    fun `processItem when item not active but in past_items automatically assigns to former location`() = runTest {
        val pastItem = PastItem(
            id = "past_soy_sauce",
            name = "Kikkoman Soy Sauce",
            barcode = "5012345678901",
            brand = "Kikkoman",
            packageQuantity = "250ml",
            shelfNumber = 1,
            zoneIndex = 2,
            trackingType = TrackingType.DISCRETE_COUNT,
            sealedCount = 1,
            isAssigned = true
        )

        val syncItem = SyncQueueItem(
            id = "sq_soy_1",
            itemId = "unassigned_placeholder_1",
            barcode = "5012345678901",
            scannedAt = 1000L,
            batchId = "batch1",
            productName = "Kikkoman Soy Sauce"
        )

        every { pantryRepository.allItems } returns flowOf(emptyList())
        coEvery { pantryRepository.getPastItemByBarcode("5012345678901") } returns pastItem

        val addSlot = slot<PantryItem>()
        coEvery { pantryRepository.addItem(capture(addSlot)) } returns Unit
        coEvery { syncQueueRepository.markAsProcessed("sq_soy_1") } returns Unit

        viewModel.processItem(syncItem)

        assertNull(viewModel.uiState.value.activeOverlay) // Auto assigned without showing overlay!
        coVerify(exactly = 1) { pantryRepository.addItem(any()) }
        coVerify(exactly = 1) { syncQueueRepository.markAsProcessed("sq_soy_1") }

        val added = addSlot.captured
        assertEquals("Kikkoman Soy Sauce", added.name)
        assertEquals(1, added.shelfNumber)
        assertEquals(2, added.zoneIndex)
    }

    @Test
    fun `openEnrichmentModal when item in past_items opens overlay with isPastItem true`() = runTest {
        val pastItem = PastItem(
            id = "past_soy_sauce",
            name = "Kikkoman Soy Sauce",
            barcode = "5012345678901",
            brand = "Kikkoman",
            packageQuantity = "250ml",
            shelfNumber = 1,
            zoneIndex = 2,
            trackingType = TrackingType.DISCRETE_COUNT,
            sealedCount = 1,
            isAssigned = true
        )

        val syncItem = SyncQueueItem(
            id = "sq_soy_1",
            itemId = "unassigned_placeholder_1",
            barcode = "5012345678901",
            scannedAt = 1000L,
            batchId = "batch1",
            productName = "Kikkoman Soy Sauce"
        )

        every { pantryRepository.allItems } returns flowOf(emptyList())
        coEvery { pantryRepository.getPastItemByBarcode("5012345678901") } returns pastItem

        viewModel.openEnrichmentModal(syncItem)

        val overlay = viewModel.uiState.value.activeOverlay
        assertTrue(overlay is OverlayContext.SyncQueueEnrichment)
        val enrichment = overlay as OverlayContext.SyncQueueEnrichment
        assertTrue(enrichment.isPastItem)
        assertEquals("Kikkoman Soy Sauce", enrichment.existingItem?.name)
        assertEquals(0 to 1, enrichment.suggestedShelf)
    }

    @Test
    fun `consumeItem when count reaches 0 moves item to past_items`() = runTest {
        val item = PantryItem(
            id = "item_1",
            name = "Milk",
            shelfNumber = 1,
            zoneIndex = 1,
            trackingType = TrackingType.DISCRETE_COUNT,
            unitsPerPack = 1,
            activeCount = 1,
            sealedCount = 1,
            isAssigned = true
        )

        every { pantryRepository.allItems } returns flowOf(listOf(item))

        coEvery { pantryRepository.moveToPastItems(any()) } returns Unit

        viewModel.consumeItem(item)

        coVerify(exactly = 1) { pantryRepository.moveToPastItems(any()) }
    }
}
