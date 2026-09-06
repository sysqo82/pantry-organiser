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
import kotlinx.coroutines.test.*
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
        every { syncQueueRepository.getPendingItems() } returns flowOf(emptyList())
        every { pantryRepository.allItems } returns flowOf(emptyList())
        viewModel = DashboardViewModel(syncQueueRepository, pantryRepository, openFoodFactsProber)
    }

    @After
    fun tearDown() {
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

        coEvery { pantryRepository.getItemByBarcode("5000462326897") } returns null

        val slot = slot<PantryItem>()
        coEvery { pantryRepository.addItem(capture(slot)) } returns Unit

        viewModel.saveEnrichedItem(
            syncItem = syncItem,
            existingItem = null,
            shelf = 3,
            zone = 2,
            quantityToAdd = 2,
            fillLevel = FillLevel.FULL
        )

        val savedItem = slot.captured
        assertEquals("British Cooking Salt", savedItem.name)
        assertEquals("5000462326897", savedItem.barcode)
        assertEquals("Tesco", savedItem.brand)
        assertEquals(3, savedItem.shelfNumber)
        assertEquals(2, savedItem.zoneIndex)
        assertEquals(TrackingType.BULK_LEVEL, savedItem.trackingType)
        assertEquals(1, savedItem.sealedCount) // 1 sealed tub + 1 open tub = 2 total
        assertEquals(FillLevel.FULL, savedItem.activeFill)
        assertTrue(savedItem.hasStock)
        assertEquals(2, savedItem.totalDisplayCount)
    }

    @Test
    fun `saveEnrichedItem updates existing item with enriched name and details if existing was blank`() = runTest {
        val existingItem = PantryItem(
            id = "existing_123",
            name = "",
            barcode = "5000462326897",
            brand = null,
            shelfNumber = 1,
            zoneIndex = 1,
            trackingType = TrackingType.BULK_LEVEL,
            sealedCount = 0,
            activeFill = FillLevel.FULL
        )

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
        coEvery { pantryRepository.updateItem(capture(slot)) } returns Unit

        viewModel.saveEnrichedItem(
            syncItem = syncItem,
            existingItem = existingItem,
            shelf = 3,
            zone = 2,
            quantityToAdd = 1,
            fillLevel = FillLevel.FULL
        )

        val updatedItem = slot.captured
        assertEquals("existing_123", updatedItem.id)
        assertEquals("British Cooking Salt", updatedItem.name)
        assertEquals("Tesco", updatedItem.brand)
        assertEquals("https://images.openfoodfacts.org/salt.jpg", updatedItem.imageUrl)
        assertEquals(3, updatedItem.shelfNumber)
        assertEquals(2, updatedItem.zoneIndex)
    }

    @Test
    fun `saveEnrichedItem updates existing item with enriched name when existing item name is Unknown Product`() = runTest {
        val existingItem = PantryItem(
            id = "existing_123",
            name = "Unknown Product",
            barcode = "5000462326897",
            brand = null,
            shelfNumber = 1,
            zoneIndex = 1,
            trackingType = TrackingType.BULK_LEVEL,
            sealedCount = 0,
            activeFill = FillLevel.FULL
        )

        val syncItem = SyncQueueItem(
            id = "batch1_item1",
            barcode = "5000462326897",
            scannedAt = 1000L,
            batchId = "batch1",
            productName = "British Cooking Salt",
            brand = "Tesco",
            imageUrl = "https://images.openfoodfacts.org/salt.jpg",
            quantity = "1.5kg"
        )

        val slot = slot<PantryItem>()
        coEvery { pantryRepository.updateItem(capture(slot)) } returns Unit

        viewModel.saveEnrichedItem(
            syncItem = syncItem,
            existingItem = existingItem,
            shelf = 3,
            zone = 2,
            quantityToAdd = 1,
            fillLevel = FillLevel.FULL
        )

        val updatedItem = slot.captured
        assertEquals("existing_123", updatedItem.id)
        assertEquals("British Cooking Salt", updatedItem.name)
        assertEquals("Tesco", updatedItem.brand)
        assertEquals("https://images.openfoodfacts.org/salt.jpg", updatedItem.imageUrl)
        assertEquals("1.5kg", updatedItem.packageQuantity)
    }

    @Test
    fun `consumeItem on bulk item decrements fill level`() = runTest {
        val bulkItem = PantryItem(
            id = "salt_1",
            name = "British Cooking Salt",
            shelfNumber = 3,
            zoneIndex = 2,
            trackingType = TrackingType.BULK_LEVEL,
            sealedCount = 0,
            activeFill = FillLevel.FULL
        )

        val slot = slot<PantryItem>()
        coEvery { pantryRepository.updateItem(capture(slot)) } returns Unit

        viewModel.consumeItem(bulkItem)

        val updated = slot.captured
        assertEquals(FillLevel.THREE_QUARTERS, updated.activeFill)
        assertEquals(0, updated.sealedCount)
    }

    @Test
    fun `consumeItem on empty bulk item with sealed reserve auto-rolls reserve`() = runTest {
        val bulkItem = PantryItem(
            id = "salt_1",
            name = "British Cooking Salt",
            shelfNumber = 3,
            zoneIndex = 2,
            trackingType = TrackingType.BULK_LEVEL,
            sealedCount = 1,
            activeFill = FillLevel.LOW
        )

        val slot = slot<PantryItem>()
        coEvery { pantryRepository.updateItem(capture(slot)) } returns Unit

        viewModel.consumeItem(bulkItem) // LOW -> EMPTY -> Auto-roll: FULL, sealedCount = 0

        val updated = slot.captured
        assertEquals(FillLevel.FULL, updated.activeFill)
        assertEquals(0, updated.sealedCount)
    }

    @Test
    fun `consumeItem on empty bulk item with no reserve moves item to past items`() = runTest {
        val bulkItem = PantryItem(
            id = "salt_1",
            name = "British Cooking Salt",
            shelfNumber = 3,
            zoneIndex = 2,
            trackingType = TrackingType.BULK_LEVEL,
            sealedCount = 0,
            activeFill = FillLevel.EMPTY
        )

        val slot = slot<PantryItem>()
        coEvery { pantryRepository.moveToPastItems(capture(slot)) } returns Unit

        viewModel.consumeItem(bulkItem)

        assertEquals("salt_1", slot.captured.id)
    }

    @Test
    fun `consumeItem on bulk item at LOW with no reserve moves item to past items`() = runTest {
        val bulkItem = PantryItem(
            id = "salt_1",
            name = "British Cooking Salt",
            shelfNumber = 3,
            zoneIndex = 2,
            trackingType = TrackingType.BULK_LEVEL,
            sealedCount = 0,
            activeFill = FillLevel.LOW
        )

        val slot = slot<PantryItem>()
        coEvery { pantryRepository.moveToPastItems(capture(slot)) } returns Unit

        viewModel.consumeItem(bulkItem)

        assertEquals("salt_1", slot.captured.id)
    }

    @Test
    fun `restockItem on empty bulk item refills active fill to FULL`() = runTest {
        val bulkItem = PantryItem(
            id = "salt_1",
            name = "British Cooking Salt",
            shelfNumber = 3,
            zoneIndex = 2,
            trackingType = TrackingType.BULK_LEVEL,
            sealedCount = 0,
            activeFill = FillLevel.EMPTY
        )

        val slot = slot<PantryItem>()
        coEvery { pantryRepository.updateItem(capture(slot)) } returns Unit

        viewModel.restockItem(bulkItem)

        val updated = slot.captured
        assertEquals(FillLevel.FULL, updated.activeFill)
        assertEquals(0, updated.sealedCount)
    }

    @Test
    fun `restockItem on non-empty bulk item adds sealed reserve`() = runTest {
        val bulkItem = PantryItem(
            id = "salt_1",
            name = "British Cooking Salt",
            shelfNumber = 3,
            zoneIndex = 2,
            trackingType = TrackingType.BULK_LEVEL,
            sealedCount = 0,
            activeFill = FillLevel.FULL
        )

        val slot = slot<PantryItem>()
        coEvery { pantryRepository.updateItem(capture(slot)) } returns Unit

        viewModel.restockItem(bulkItem)

        val updated = slot.captured
        assertEquals(FillLevel.FULL, updated.activeFill)
        assertEquals(1, updated.sealedCount)
    }

    @Test
    fun `consumeItem on multipack decrements activeCount and auto-rolls when empty`() = runTest {
        val multipackItem = PantryItem(
            id = "corn_1",
            name = "Sweetcorn in water 3x200g",
            shelfNumber = 4,
            zoneIndex = 2,
            trackingType = TrackingType.DISCRETE_COUNT,
            unitsPerPack = 3,
            activeCount = 1,
            sealedCount = 1
        )

        val slot = slot<PantryItem>()
        coEvery { pantryRepository.updateItem(capture(slot)) } returns Unit

        viewModel.consumeItem(multipackItem, 1)

        val updated = slot.captured
        assertEquals(3, updated.activeCount) // Auto-rolled sealed multipack (3 units active)
        assertEquals(0, updated.sealedCount) // Sealed reserve decremented to 0
        assertEquals(3, updated.totalDisplayCount) // Total = 3 units
    }

    @Test
    fun `updateFillLevel to EMPTY with sealed reserve auto-rolls next container`() = runTest {
        val bulkItem = PantryItem(
            id = "salt_1",
            name = "British Cooking Salt",
            shelfNumber = 3,
            zoneIndex = 2,
            trackingType = TrackingType.BULK_LEVEL,
            sealedCount = 1,
            activeFill = FillLevel.FULL
        )

        val slot = slot<PantryItem>()
        coEvery { pantryRepository.updateItem(capture(slot)) } returns Unit

        viewModel.updateFillLevel(bulkItem, FillLevel.EMPTY)

        val updated = slot.captured
        assertEquals(FillLevel.FULL, updated.activeFill)
        assertEquals(0, updated.sealedCount)
    }

    @Test
    fun `saveEnrichedItem on unassigned multipack sets 0 sealed reserve packs giving 3 total units`() = runTest {
        val unassignedItem = PantryItem(
            id = "3fuvis3959ghsr2",
            name = "Sweetcorn in water",
            barcode = "5063445794342",
            brand = "Tesco",
            packageQuantity = "3 x 200 gr",
            shelfNumber = 1,
            zoneIndex = 1,
            trackingType = TrackingType.DISCRETE_COUNT,
            unitsPerPack = 3,
            activeCount = 3,
            sealedCount = 0,
            isAssigned = false
        )

        val syncItem = SyncQueueItem(
            id = "sq_1",
            itemId = "3fuvis3959ghsr2",
            barcode = "5063445794342",
            scannedAt = 1000L,
            batchId = "batch1",
            productName = "Sweetcorn in water",
            brand = "Tesco",
            quantity = "3 x 200 gr"
        )

        val slot = slot<PantryItem>()
        coEvery { pantryRepository.updateItem(capture(slot)) } returns Unit

        viewModel.saveEnrichedItem(
            syncItem = syncItem,
            existingItem = unassignedItem,
            shelf = 3,
            zone = 2,
            quantityToAdd = 1,
            fillLevel = FillLevel.FULL
        )

        val saved = slot.captured
        assertEquals("3fuvis3959ghsr2", saved.id)
        assertTrue(saved.isAssigned)
        assertEquals(3, saved.shelfNumber)
        assertEquals(2, saved.zoneIndex)
        assertEquals(3, saved.unitsPerPack)
        assertEquals(3, saved.activeCount)
        assertEquals(0, saved.sealedCount) // 0 sealed reserve packs for 1 multipack
        assertEquals(3, saved.totalDisplayCount) // Total 3 cans/tins
    }

    @Test
    fun `saveEnrichedItem on already assigned multipack adds a sealed reserve pack`() = runTest {
        val assignedItem = PantryItem(
            id = "3fuvis3959ghsr2",
            name = "Sweetcorn in water",
            barcode = "5063445794342",
            brand = "Tesco",
            packageQuantity = "3 x 200 gr",
            shelfNumber = 3,
            zoneIndex = 2,
            trackingType = TrackingType.DISCRETE_COUNT,
            unitsPerPack = 3,
            activeCount = 3,
            sealedCount = 0,
            isAssigned = true
        )

        val syncItem = SyncQueueItem(
            id = "sq_2",
            itemId = "3fuvis3959ghsr2",
            barcode = "5063445794342",
            scannedAt = 2000L,
            batchId = "batch2",
            productName = "Sweetcorn in water",
            brand = "Tesco",
            quantity = "3 x 200 gr"
        )

        val slot = slot<PantryItem>()
        coEvery { pantryRepository.updateItem(capture(slot)) } returns Unit

        viewModel.saveEnrichedItem(
            syncItem = syncItem,
            existingItem = assignedItem,
            shelf = 3,
            zone = 2,
            quantityToAdd = 1,
            fillLevel = FillLevel.FULL
        )

        val saved = slot.captured
        assertEquals("3fuvis3959ghsr2", saved.id)
        assertTrue(saved.isAssigned)
        assertEquals(3, saved.unitsPerPack)
        assertEquals(3, saved.activeCount)
        assertEquals(1, saved.sealedCount) // 1 sealed reserve pack added
        assertEquals(6, saved.totalDisplayCount) // Total 6 cans/tins (2 3-packs)
    }

    @Test
    fun `consumeItem when no overlay active updates item in repository without opening overlay`() = runTest {
        val multipackItem = PantryItem(
            id = "corn_1",
            name = "Sweetcorn in water 3x200g",
            shelfNumber = 4,
            zoneIndex = 2,
            trackingType = TrackingType.DISCRETE_COUNT,
            unitsPerPack = 3,
            activeCount = 3,
            sealedCount = 0
        )

        val slot = slot<PantryItem>()
        coEvery { pantryRepository.updateItem(capture(slot)) } returns Unit

        assertNull(viewModel.uiState.value.activeOverlay)

        viewModel.consumeItem(multipackItem, 1)

        val updated = slot.captured
        assertEquals(2, updated.activeCount)
        assertEquals(2, updated.totalDisplayCount)
        assertNull(viewModel.uiState.value.activeOverlay) // Overlay remains null
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

        // Expected spatial order: S4-M (shelf 4, zone 2), S4-R (shelf 4, zone 3), S1-L (shelf 1, zone 1)
        assertEquals(3, stateItems.size)
        assertEquals("Pizza Topper", stateItems[0].name)
        assertEquals("Soy Sauce", stateItems[1].name)
        assertEquals("Salt", stateItems[2].name)
    }

    @Test
    fun `processItem and saveEnrichedItem merges into existing assigned item with same barcode and deletes unassigned duplicate`() = runTest {
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

        vm.processItem(syncItem)

        val overlay = vm.uiState.value.activeOverlay
        assertTrue(overlay is OverlayContext.SyncQueueEnrichment)
        val enrichment = overlay as OverlayContext.SyncQueueEnrichment
        assertEquals("assigned_1", enrichment.existingItem?.id) // Should select existing assigned item!

        val updateSlot = slot<PantryItem>()
        val deleteSlot = slot<PantryItem>()
        coEvery { pantryRepository.updateItem(capture(updateSlot)) } returns Unit
        coEvery { pantryRepository.deleteItem(capture(deleteSlot)) } returns Unit

        vm.saveEnrichedItem(
            syncItem = syncItem,
            existingItem = enrichment.existingItem,
            shelf = 3,
            zone = 2,
            quantityToAdd = 1,
            fillLevel = FillLevel.FULL
        )

        val updated = updateSlot.captured
        assertEquals("assigned_1", updated.id)
        assertTrue(updated.isAssigned)

        val deleted = deleteSlot.captured
        assertEquals("unassigned_2", deleted.id)
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
    fun `processItem when item not active but in past_items suggests last location and sets isPastItem true`() = runTest {
        val pastItem = PastItem(
            id = "past_soy_sauce",
            name = "Kikkoman Soy Sauce",
            barcode = "5012345678901",
            brand = "Kikkoman",
            packageQuantity = "250ml",
            shelfNumber = 4,
            zoneIndex = 2, // Shelf 4, Zone 2 -> Row 0, Col 1 (S4-M)
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

        val unassignedPantryItem = PantryItem(
            id = "unassigned_placeholder_1",
            name = "Kikkoman Soy Sauce",
            barcode = "5012345678901",
            shelfNumber = 1,
            zoneIndex = 1,
            isAssigned = false
        )

        every { pantryRepository.allItems } returns flowOf(listOf(unassignedPantryItem))
        coEvery { pantryRepository.getItemByBarcode("5012345678901") } returns unassignedPantryItem
        coEvery { pantryRepository.getPastItemByBarcode("5012345678901") } returns pastItem

        viewModel.processItem(syncItem)

        val overlay = viewModel.uiState.value.activeOverlay
        assertTrue(overlay is OverlayContext.SyncQueueEnrichment)
        val enrichment = overlay as OverlayContext.SyncQueueEnrichment
        assertTrue(enrichment.isPastItem)
        assertEquals("Kikkoman Soy Sauce", enrichment.existingItem?.name)
        assertEquals(0 to 1, enrichment.suggestedShelf) // Shelf 4, Zone 2 -> Row 0, Col 1 (S4-M)
    }

    @Test
    fun `consumeItem when count reaches 0 moves item to past_items`() = runTest {
        val discreteItem = PantryItem(
            id = "soy_sauce_1",
            name = "Kikkoman Soy Sauce",
            barcode = "5012345678901",
            shelfNumber = 4,
            zoneIndex = 3,
            trackingType = TrackingType.DISCRETE_COUNT,
            sealedCount = 1,
            isAssigned = true
        )

        val slot = slot<PantryItem>()
        coEvery { pantryRepository.moveToPastItems(capture(slot)) } returns Unit

        viewModel.consumeItem(discreteItem, 1)

        val moved = slot.captured
        assertEquals("soy_sauce_1", moved.id)
        assertEquals("Kikkoman Soy Sauce", moved.name)
        assertEquals("5012345678901", moved.barcode)
    }

    @Test
    fun `saveEnrichedItem when re-adding past item calls addItem to insert into pantry_items`() = runTest {
        val pastAsPantry = PantryItem(
            id = "past_soy_sauce_1",
            name = "Kikkoman Soy Sauce",
            barcode = "5012345678901",
            brand = "Kikkoman",
            packageQuantity = "250ml",
            shelfNumber = 4,
            zoneIndex = 2,
            trackingType = TrackingType.DISCRETE_COUNT,
            unitsPerPack = 1,
            sealedCount = 0,
            isAssigned = false // Unassigned past item template!
        )

        val syncItem = SyncQueueItem(
            id = "sq_soy_1",
            barcode = "5012345678901",
            scannedAt = 1000L,
            batchId = "batch1",
            productName = "Kikkoman Soy Sauce"
        )

        val addSlot = slot<PantryItem>()
        coEvery { pantryRepository.addItem(capture(addSlot)) } returns Unit

        viewModel.saveEnrichedItem(
            syncItem = syncItem,
            existingItem = pastAsPantry,
            shelf = 4,
            zone = 2,
            quantityToAdd = 1,
            fillLevel = FillLevel.FULL,
            isPastItem = true
        )

        coVerify(exactly = 1) { pantryRepository.addItem(any()) }
        coVerify(exactly = 0) { pantryRepository.updateItem(any()) }

        val saved = addSlot.captured
        assertTrue(saved.isAssigned)
        assertEquals("Kikkoman Soy Sauce", saved.name)
        assertEquals("5012345678901", saved.barcode)
        assertEquals(4, saved.shelfNumber)
        assertEquals(2, saved.zoneIndex)
        assertEquals(FillLevel.FULL, saved.activeFill)
    }
}
