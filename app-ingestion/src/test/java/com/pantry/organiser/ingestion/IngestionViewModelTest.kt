package com.pantry.organiser.ingestion

import app.cash.turbine.test
import com.pantry.organiser.core.model.BatchPayload
import com.pantry.organiser.core.model.PantryItem
import com.pantry.organiser.core.network.OffProduct
import com.pantry.organiser.core.network.OpenFoodFactsRepository
import com.pantry.organiser.core.network.SyncService
import com.pantry.organiser.ingestion.scanner.ContinuousScanner
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class IngestionViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private val scanner: ContinuousScanner = mockk(relaxed = true)
    private val feedbackController: FeedbackController = mockk(relaxed = true)
    private val syncService: SyncService = mockk(relaxed = true)
    private val offRepository: OpenFoodFactsRepository = mockk(relaxed = true)

    private val barcodeFlow = MutableSharedFlow<String>()

    private lateinit var viewModel: IngestionViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        mockkStatic(android.util.Log::class)
        every { android.util.Log.d(any<String>(), any<String>()) } returns 0
        every { android.util.Log.e(any<String>(), any<String>()) } returns 0
        every { android.util.Log.e(any<String>(), any<String>(), any()) } returns 0
        every { android.util.Log.w(any<String>(), any<String>()) } returns 0
        every { android.util.Log.i(any<String>(), any<String>()) } returns 0

        every { scanner.barcodes } returns barcodeFlow
        every { feedbackController.effects } returns flowOf()
        viewModel = IngestionViewModel(scanner, feedbackController, syncService, offRepository)
    }

    @After
    fun tearDown() {
        unmockkStatic(android.util.Log::class)
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state is HOME with empty items`() {
        val state = viewModel.uiState.value
        assertEquals(IngestionMode.HOME, state.mode)
        assertTrue(state.scannedItems.isEmpty())
        assertTrue(state.items.isEmpty())
        assertFalse(state.isSending)
    }

    @Test
    fun `setMode updates UI state mode`() {
        viewModel.setMode(IngestionMode.INSERT)
        assertEquals(IngestionMode.INSERT, viewModel.uiState.value.mode)

        viewModel.setMode(IngestionMode.CHECK)
        assertEquals(IngestionMode.CHECK, viewModel.uiState.value.mode)
    }

    @Test
    fun `scanning barcode in CHECK mode signals success and returns to HOME`() = runTest {
        viewModel.setMode(IngestionMode.CHECK)

        barcodeFlow.emit("5000123456789")
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(IngestionMode.HOME, viewModel.uiState.value.mode)
        verify { feedbackController.signalSuccess() }
    }

    @Test
    fun `scanning barcode in INSERT mode enriches item via OpenFoodFacts`() = runTest {
        viewModel.setMode(IngestionMode.INSERT)

        val mockProduct = OffProduct(
            productName = "Tesco Milk",
            brands = "Tesco",
            imageUrl = "https://example.com/milk.jpg",
            weight = "1L"
        )
        coEvery { offRepository.getProduct("5000123456789") } returns mockProduct

        barcodeFlow.emit("5000123456789")
        testDispatcher.scheduler.advanceUntilIdle()

        val scanned = viewModel.uiState.value.scannedItems
        assertEquals(1, scanned.size)
        assertEquals("5000123456789", scanned[0].barcode)
        assertEquals("Tesco Milk", scanned[0].productName)
        assertEquals("Tesco", scanned[0].brand)
        assertEquals("https://example.com/milk.jpg", scanned[0].imageUrl)
        assertEquals("1L", scanned[0].quantity)

        verify { feedbackController.signalSuccess() }
    }

    @Test
    fun `scanning duplicate barcode in INSERT mode signals duplicate without adding new item`() = runTest {
        viewModel.setMode(IngestionMode.INSERT)

        coEvery { offRepository.getProduct("5000123456789") } returns null

        barcodeFlow.emit("5000123456789")
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(1, viewModel.uiState.value.scannedItems.size)

        // Rescan duplicate
        barcodeFlow.emit("5000123456789")
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, viewModel.uiState.value.scannedItems.size)
        verify { feedbackController.signalDuplicate() }
    }

    @Test
    fun `scanning barcode in INSERT mode sets Unknown Product when OFF returns null`() = runTest {
        viewModel.setMode(IngestionMode.INSERT)
        coEvery { offRepository.getProduct("1111111111111") } returns null

        barcodeFlow.emit("1111111111111")
        testDispatcher.scheduler.advanceUntilIdle()

        val scanned = viewModel.uiState.value.scannedItems
        assertEquals(1, scanned.size)
        assertEquals("Unknown Product", scanned[0].productName)
    }

    @Test
    fun `scanning barcode in INSERT mode sets Network Error when OFF repository throws exception`() = runTest {
        viewModel.setMode(IngestionMode.INSERT)
        coEvery { offRepository.getProduct("2222222222222") } throws RuntimeException("Network down")

        barcodeFlow.emit("2222222222222")
        testDispatcher.scheduler.advanceUntilIdle()

        val scanned = viewModel.uiState.value.scannedItems
        assertEquals(1, scanned.size)
        assertEquals("Network Error", scanned[0].productName)
    }

    @Test
    fun `sendToPantry dispatches batch payload with itemIds and resets mode to HOME`() = runTest {
        viewModel.setMode(IngestionMode.INSERT)
        coEvery { offRepository.getProduct(any()) } returns null
        coEvery { syncService.createPantryItem(any()) } returns PantryItem(id = "pb_id_123", name = "Test Item", barcode = "5000123456789", shelfNumber = 1, zoneIndex = 1)

        barcodeFlow.emit("5000123456789")
        testDispatcher.scheduler.advanceUntilIdle()

        val slot = slot<BatchPayload>()
        coEvery { syncService.dispatchBatch(capture(slot)) } returns Unit

        viewModel.sendToPantry()
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify { syncService.dispatchBatch(any()) }
        assertEquals("default-pantry", slot.captured.pantryId)
        assertEquals(1, slot.captured.safeItemIds.size)
        assertEquals("pb_id_123", slot.captured.safeItemIds[0])

        val state = viewModel.uiState.value
        assertTrue(state.scannedItems.isEmpty())
        assertFalse(state.isSending)
        assertEquals(IngestionMode.HOME, state.mode)
    }

    @Test
    fun `sendToPantry resets isSending to false on exception`() = runTest {
        viewModel.setMode(IngestionMode.INSERT)
        coEvery { offRepository.getProduct(any()) } returns null

        barcodeFlow.emit("5000123456789")
        testDispatcher.scheduler.advanceUntilIdle()

        coEvery { syncService.dispatchBatch(any()) } throws RuntimeException("Server error")

        viewModel.sendToPantry()
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isSending)
        assertEquals(1, state.scannedItems.size)
    }

    @Test
    fun `startRealtimeSync fetches initial items and observes updates`() = runTest {
        val initialItems = listOf(
            PantryItem(id = "1", name = "Sweetcorn", barcode = "100", shelfNumber = 4, zoneIndex = 2, isAssigned = true)
        )
        val realtimeFlow = MutableSharedFlow<PantryItem>()

        coEvery { syncService.fetchPantryItems("default-pantry") } returns initialItems
        every { syncService.observePantryItems("default-pantry") } returns realtimeFlow

        viewModel.startRealtimeSync()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, viewModel.uiState.value.items.size)
        assertEquals("Sweetcorn", viewModel.uiState.value.items[0].name)

        // Emit new realtime item
        val newItem = PantryItem(id = "2", name = "Soy Sauce", barcode = "200", shelfNumber = 3, zoneIndex = 3, isAssigned = true)
        realtimeFlow.emit(newItem)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(2, viewModel.uiState.value.items.size)
        assertEquals("Soy Sauce", viewModel.uiState.value.items[1].name)

        // Emit deleted item event (sealedCount = -1)
        val deletedItem = PantryItem(id = "1", name = "Sweetcorn", barcode = "100", shelfNumber = 4, zoneIndex = 2, isAssigned = true, sealedCount = -1)
        realtimeFlow.emit(deletedItem)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, viewModel.uiState.value.items.size)
        assertEquals("Soy Sauce", viewModel.uiState.value.items[0].name)
    }
}
