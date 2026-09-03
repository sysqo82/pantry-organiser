package com.pantry.organiser.dashboard

import com.pantry.organiser.core.model.FillLevel
import com.pantry.organiser.core.model.PantryItem
import com.pantry.organiser.core.model.TrackingType
import com.pantry.organiser.dashboard.data.PantryRepository
import com.pantry.organiser.dashboard.data.SyncQueueItem
import com.pantry.organiser.dashboard.data.SyncQueueRepository
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
    private lateinit var viewModel: DashboardViewModel
    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        every { syncQueueRepository.getPendingItems() } returns flowOf(emptyList())
        every { pantryRepository.allItems } returns flowOf(emptyList())
        viewModel = DashboardViewModel(syncQueueRepository, pantryRepository)
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
    fun `consumeItem on empty bulk item with no reserve deletes item`() = runTest {
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
        coEvery { pantryRepository.deleteItem(capture(slot)) } returns Unit

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
}
