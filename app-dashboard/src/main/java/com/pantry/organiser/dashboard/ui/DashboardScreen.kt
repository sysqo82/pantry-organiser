package com.pantry.organiser.dashboard.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.pantry.organiser.core.model.FillLevel
import com.pantry.organiser.core.model.PantryItem
import com.pantry.organiser.dashboard.DashboardViewModel
import com.pantry.organiser.dashboard.data.SyncQueueItem
import com.pantry.organiser.dashboard.ui.components.EditItemBottomSheet
import com.pantry.organiser.dashboard.ui.components.ItemDetailActionModal
import com.pantry.organiser.dashboard.ui.components.PrototypeSwitcher
import com.pantry.organiser.dashboard.ui.variants.VariantACommandCenter
import com.pantry.organiser.dashboard.ui.variants.VariantBSplitWorkbench
import com.pantry.organiser.dashboard.ui.variants.VariantCZoneTacticalMap

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel
) {
    val uiState by viewModel.uiState.collectAsState()
    val configuration = LocalConfiguration.current
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START) {
                viewModel.startRealtimeSync()
            } else if (event == Lifecycle.Event.ON_STOP) {
                viewModel.stopRealtimeSync()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            viewModel.stopRealtimeSync()
        }
    }

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.probeMessage) {
        uiState.probeMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearProbeMessage()
        }
    }

    Scaffold(
        topBar = { },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            DashboardLayout(
                pendingItems = uiState.pendingItems,
                pantryItems = uiState.pantryItems,
                onProcessItem = { viewModel.processItem(it) },
                onSelectItem = { viewModel.selectItem(it) },
                onConsume = { item -> viewModel.consumeItem(item) },
                onRestock = { item -> viewModel.restockItem(item) },
                onUpdateFillLevel = { item, fillLevel -> viewModel.updateFillLevel(item, fillLevel) },
                onClearPendingItem = { viewModel.clearPendingItem(it) },
                onClearAllPending = { viewModel.clearAllPendingItems() }
            )
        }
    }

    // Active Overlays
    uiState.activeOverlay?.let { overlay ->
        when (overlay) {
            is OverlayContext.SyncQueueEnrichment -> {
                EnrichmentOverlay(
                    syncItem = overlay.syncItem,
                    existingItem = overlay.existingItem,
                    isPastItem = overlay.isPastItem,
                    suggestedShelf = overlay.suggestedShelf,
                    onSave = { shelf, zone, qty, fill ->
                        viewModel.saveEnrichedItem(overlay.syncItem, overlay.existingItem, shelf, zone, qty, fill, overlay.isPastItem)
                    },
                    onDismiss = { viewModel.dismissOverlay() }
                )
            }
            is OverlayContext.ItemDetail -> {
                ItemDetailActionModal(
                    item = overlay.item,
                    onConsume = { amount -> viewModel.consumeItem(overlay.item, amount) },
                    onRestock = { viewModel.restockItem(overlay.item) },
                    onEdit = { viewModel.editItem(overlay.item) },
                    onUpdateLevel = { fillLevel -> viewModel.updateFillLevel(overlay.item, fillLevel) },
                    onDismiss = { viewModel.dismissOverlay() }
                )
            }
            is OverlayContext.ItemEdit -> {
                EditItemBottomSheet(
                    item = overlay.item,
                    onSave = { updatedItem -> viewModel.saveEditedItem(updatedItem) },
                    onDelete = { itemToDelete -> viewModel.deleteItem(itemToDelete) },
                    onDismiss = { viewModel.dismissOverlay() }
                )
            }
            is OverlayContext.ManualEntry -> {
                // Manual entry if needed
            }
        }
    }
}

@Composable
fun DashboardLayout(
    pendingItems: List<SyncQueueItem>,
    pantryItems: List<PantryItem>,
    onProcessItem: (SyncQueueItem) -> Unit,
    onSelectItem: (PantryItem) -> Unit,
    onConsume: (PantryItem) -> Unit,
    onRestock: (PantryItem) -> Unit,
    onUpdateFillLevel: (PantryItem, FillLevel) -> Unit,
    onClearPendingItem: (SyncQueueItem) -> Unit = {},
    onClearAllPending: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var currentVariant by remember { mutableStateOf("A") }

    Box(modifier = modifier.fillMaxSize()) {
        when (currentVariant) {
            "A" -> VariantACommandCenter(
                pantryItems = pantryItems,
                pendingItems = pendingItems,
                onProcessItem = onProcessItem,
                onSelectItem = onSelectItem,
                onConsume = onConsume,
                onRestock = onRestock,
                onUpdateFillLevel = onUpdateFillLevel,
                onClearAllPending = onClearAllPending
            )
            "B" -> VariantBSplitWorkbench(
                pantryItems = pantryItems,
                pendingItems = pendingItems,
                onProcessItem = onProcessItem,
                onSelectItem = onSelectItem,
                onConsume = onConsume,
                onRestock = onRestock,
                onUpdateFillLevel = onUpdateFillLevel,
                onClearAllPending = onClearAllPending
            )
            "C" -> VariantCZoneTacticalMap(
                pantryItems = pantryItems,
                pendingItems = pendingItems,
                onProcessItem = onProcessItem,
                onSelectItem = onSelectItem,
                onConsume = onConsume,
                onRestock = onRestock,
                onUpdateFillLevel = onUpdateFillLevel,
                onClearAllPending = onClearAllPending
            )
        }

        PrototypeSwitcher(
            currentVariant = currentVariant,
            onVariantChange = { currentVariant = it },
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}
