package com.pantry.organiser.dashboard.data

import android.util.Log
import com.pantry.organiser.core.model.BatchPayload
import com.pantry.organiser.core.model.ScannedItem
import com.pantry.organiser.core.network.OpenFoodFactsRepository
import com.pantry.organiser.core.network.SyncService
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SyncQueueRepositoryTest {

    private val testDispatcher = StandardTestDispatcher()

    private val syncService = mockk<SyncService>(relaxed = true)
    private val offRepository = mockk<OpenFoodFactsRepository>(relaxed = true)
    private val syncQueueDao = mockk<SyncQueueDao>(relaxed = true)

    // In-memory fake database state for sync_queue
    private val dbSyncQueue = mutableMapOf<String, SyncQueueItem>()
    private val pendingItemsFlow = MutableStateFlow<List<SyncQueueItem>>(emptyList())

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        mockkStatic(Log::class)
        every { Log.d(any(), any()) } returns 0
        every { Log.e(any(), any()) } returns 0
        every { Log.e(any(), any(), any()) } returns 0
        every { Log.w(any(), any<String>()) } returns 0

        dbSyncQueue.clear()

        // Mock Dao behavior to match real Room Dao logic
        coEvery { syncQueueDao.insertItems(any()) } answers {
            val items = firstArg<List<SyncQueueItem>>()
            items.forEach { item ->
                if (!dbSyncQueue.containsKey(item.id)) {
                    dbSyncQueue[item.id] = item
                }
            }
            updateFlow()
        }

        coEvery { syncQueueDao.getPendingItems() } answers {
            pendingItemsFlow
        }

        coEvery { syncQueueDao.markAsProcessedByIdOrItemId(any(), any()) } answers {
            val id = firstArg<String>()
            val itemId = secondArg<String>()
            dbSyncQueue.forEach { (key, item) ->
                if (item.id == id || (itemId.isNotBlank() && item.itemId == itemId)) {
                    dbSyncQueue[key] = item.copy(isProcessed = true)
                }
            }
            updateFlow()
        }

        coEvery { syncQueueDao.markAllAsProcessed() } answers {
            dbSyncQueue.forEach { (key, item) ->
                if (!item.isProcessed) {
                    dbSyncQueue[key] = item.copy(isProcessed = true)
                }
            }
            updateFlow()
        }

        coEvery { syncQueueDao.markAsProcessed(any()) } answers {
            val key = firstArg<String>()
            val item = dbSyncQueue[key]
            if (item != null) {
                dbSyncQueue[key] = item.copy(isProcessed = true)
            }
            updateFlow()
        }
    }

    @After
    fun tearDown() {
        unmockkStatic(Log::class)
        Dispatchers.resetMain()
    }

    private fun updateFlow() {
        pendingItemsFlow.value = dbSyncQueue.values.filter { !it.isProcessed }
    }

    @Test
    fun `cleared pending items do not reappear on subsequent batch fetches`() = runTest {
        // Arrange: 9 items from a batch payload
        val scannedItems = (1..9).map { index ->
            ScannedItem(
                barcode = "12345678900$index",
                productName = "Shopping Item $index",
                timestamp = 1000L + index
            )
        }
        val batchPayload = BatchPayload(
            pantryId = "default-pantry",
            timestamp = 1000L,
            items = scannedItems
        )

        coEvery { syncService.fetchBatches("default-pantry") } returns listOf(batchPayload)
        coEvery { syncService.fetchPantryItems("default-pantry") } returns emptyList()

        val repository = SyncQueueRepository(syncService, offRepository, syncQueueDao, UnconfinedTestDispatcher(testScheduler))

        // 1. Initial fetch inserts 9 items into sync_queue
        repository.startObserving("default-pantry")
        advanceUntilIdle()

        var pending = repository.getPendingItems().first()
        assertEquals(9, pending.size)

        // 2. User clears all 9 items from the "From Shopping List" tab
        repository.clearAllPendingItems()
        advanceUntilIdle()

        pending = repository.getPendingItems().first()
        assertEquals(0, pending.size)

        // 3. Subsequent fetch / app restart / re-sync occurs
        repository.stopObserving()
        repository.startObserving("default-pantry")
        advanceUntilIdle()

        // Assert: Pending items should STILL be 0, items should NOT reappear!
        pending = repository.getPendingItems().first()
        assertEquals(0, pending.size)
    }

    @Test
    fun `individually cleared pending item does not reappear on subsequent batch fetches`() = runTest {
        val scannedItems = (1..9).map { index ->
            ScannedItem(
                barcode = "12345678900$index",
                productName = "Shopping Item $index",
                timestamp = 1000L + index
            )
        }
        val batchPayload = BatchPayload(
            pantryId = "default-pantry",
            timestamp = 1000L,
            items = scannedItems
        )

        coEvery { syncService.fetchBatches("default-pantry") } returns listOf(batchPayload)
        coEvery { syncService.fetchPantryItems("default-pantry") } returns emptyList()

        val repository = SyncQueueRepository(syncService, offRepository, syncQueueDao, UnconfinedTestDispatcher(testScheduler))

        // 1. Initial fetch
        repository.startObserving("default-pantry")
        advanceUntilIdle()

        var pending = repository.getPendingItems().first()
        assertEquals(9, pending.size)

        // 2. Clear item 1 individually
        val itemToClear = pending.first()
        repository.clearPendingItem(itemToClear)
        advanceUntilIdle()

        pending = repository.getPendingItems().first()
        assertEquals(8, pending.size)

        // 3. Subsequent fetch / re-sync occurs
        repository.stopObserving()
        repository.startObserving("default-pantry")
        advanceUntilIdle()

        // Assert: Pending items should stay 8, cleared item must NOT reappear!
        pending = repository.getPendingItems().first()
        assertEquals(8, pending.size)
    }
}
