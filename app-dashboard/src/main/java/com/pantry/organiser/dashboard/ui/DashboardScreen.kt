package com.pantry.organiser.dashboard.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pantry.organiser.core.model.PantryConstants
import com.pantry.organiser.core.model.PantryItem
import com.pantry.organiser.core.model.TrackingType
import com.pantry.organiser.dashboard.DashboardViewModel
import com.pantry.organiser.dashboard.data.SyncQueueItem
import com.pantry.organiser.dashboard.ui.components.EditItemBottomSheet
import com.pantry.organiser.dashboard.ui.components.ItemDetailActionModal
import com.pantry.organiser.dashboard.ui.components.ProductThumbnail

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel
) {
    val uiState by viewModel.uiState.collectAsState()
    val configuration = LocalConfiguration.current
    val isTablet = configuration.screenWidthDp >= 600
    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_START) {
                viewModel.startRealtimeSync()
            } else if (event == androidx.lifecycle.Lifecycle.Event.ON_STOP) {
                viewModel.stopRealtimeSync()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            viewModel.stopRealtimeSync()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            topBar = {
                if (!isTablet) {
                    CenterAlignedTopAppBar(
                        title = { Text("Pantry Dashboard", fontWeight = FontWeight.Bold) }
                    )
                }
            }
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
                    onRestock = { item -> viewModel.restockItem(item) }
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
                        onSave = { shelf, zone, qty, fill ->
                            viewModel.saveEnrichedItem(overlay.syncItem, overlay.existingItem, shelf, zone, qty, fill)
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
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardLayout(
    pendingItems: List<SyncQueueItem>,
    pantryItems: List<PantryItem>,
    onProcessItem: (SyncQueueItem) -> Unit,
    onSelectItem: (PantryItem) -> Unit,
    onConsume: (PantryItem) -> Unit,
    onRestock: (PantryItem) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedTab by remember { mutableIntStateOf(0) } // 0: Inventory, 1: Sync Queue

    Column(modifier = modifier.fillMaxSize().padding(12.dp)) {
        val displayedItems = remember(pantryItems) { pantryItems.filter { it.isAssigned && it.hasStock } }

        // Segmented Control Tabs
        PrimaryTabRow(selectedTabIndex = selectedTab) {
            Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("Stock Inventory (${displayedItems.size})", fontWeight = FontWeight.Bold) })
            Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("From Shopping List (${pendingItems.size})", fontWeight = FontWeight.Bold) })
        }

        Spacer(Modifier.height(12.dp))

        if (selectedTab == 0) {
            if (displayedItems.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Pantry is empty", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 220.dp),
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(bottom = 24.dp)
                ) {
                    items(displayedItems, key = { it.id }) { item ->
                        ElevatedCard(
                            onClick = { onSelectItem(item) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                // Product Image + Location Tag
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(110.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    ProductThumbnail(
                                        imageUrl = item.imageUrl,
                                        apiImageUrl = item.apiImageUrl,
                                        localImageUri = item.localImageUri,
                                        itemName = item.name,
                                        thumbnailSize = null,
                                        updatedAt = item.updatedAt,
                                        modifier = Modifier.fillMaxSize()
                                    )

                                    // Location Badge Overlay
                                    Surface(
                                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.9f),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier
                                            .align(Alignment.TopStart)
                                            .padding(4.dp)
                                    ) {
                                        Text(
                                            text = "S${item.shelfNumber}-${PantryConstants.getZoneLabel(item.zoneIndex)}",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }

                                Spacer(Modifier.height(8.dp))

                                // Item Name
                                Text(
                                    text = item.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 2,
                                    modifier = Modifier.fillMaxWidth()
                                )

                                // Brand & Package Quantity
                                Text(
                                    text = "${item.brand ?: "No Brand"} · ${item.packageQuantity ?: ""}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    modifier = Modifier.fillMaxWidth()
                                )

                                Spacer(Modifier.height(8.dp))

                                // Tracking Type Badge
                                Surface(
                                    color = if (item.trackingType == TrackingType.BULK_LEVEL)
                                        MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f)
                                    else MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        text = if (item.trackingType == TrackingType.BULK_LEVEL) "Staple (${item.activeFill.label})" else if (item.unitsPerPack > 1) "${item.unitsPerPack}-Pack Multipack" else "Discrete Unit",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }

                                Spacer(Modifier.height(8.dp))

                                // Stock Level Pill
                                Surface(
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = "Stock: ${item.formattedStockText}",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                    )
                                }

                                Spacer(Modifier.height(8.dp))

                                // Quick Action Buttons (- / +)
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = { onConsume(item) },
                                        modifier = Modifier.weight(1f).height(36.dp),
                                        contentPadding = PaddingValues(0.dp)
                                    ) {
                                        Icon(Icons.Default.Remove, contentDescription = "Consume", modifier = Modifier.size(16.dp))
                                    }

                                    Button(
                                        onClick = { onRestock(item) },
                                        modifier = Modifier.weight(1f).height(36.dp),
                                        contentPadding = PaddingValues(0.dp)
                                    ) {
                                        Icon(Icons.Default.Add, contentDescription = "Restock", modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (pendingItems.isEmpty()) {
                    item { Text("No pending scans", modifier = Modifier.padding(16.dp)) }
                }
                items(pendingItems, key = { it.id.ifBlank { "${it.barcode}_${it.scannedAt}" } }) { item ->
                    ElevatedCard(onClick = { onProcessItem(item) }, modifier = Modifier.fillMaxWidth()) {
                        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(item.productName ?: "Scanning...", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Text("Barcode: ${item.barcode}", style = MaterialTheme.typography.bodySmall)
                            }
                            Button(onClick = { onProcessItem(item) }) {
                                Text("Assign Shelf")
                            }
                        }
                    }
                }
                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }
}
