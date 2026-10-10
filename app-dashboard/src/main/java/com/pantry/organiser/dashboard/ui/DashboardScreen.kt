package com.pantry.organiser.dashboard.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.pantry.organiser.core.model.FillLevel
import com.pantry.organiser.core.model.PantryConstants
import com.pantry.organiser.core.model.PantryItem
import com.pantry.organiser.core.model.PastItem
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
                pastItems = uiState.pastItems,
                onProcessItem = { viewModel.processItem(it) },
                onOpenEnrichmentModal = { viewModel.openEnrichmentModal(it) },
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

/**
 * Integrated Split Workbench Dashboard Layout
 * Features side-by-side Pantry Workbench and Shopping Intake Station on wide screens/tablets,
 * and adaptive segmented tabs on mobile devices.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardLayout(
    pendingItems: List<SyncQueueItem>,
    pantryItems: List<PantryItem>,
    pastItems: List<PastItem>,
    onProcessItem: (SyncQueueItem) -> Unit,
    onOpenEnrichmentModal: (SyncQueueItem) -> Unit,
    onSelectItem: (PantryItem) -> Unit,
    onConsume: (PantryItem) -> Unit,
    onRestock: (PantryItem) -> Unit,
    onUpdateFillLevel: (PantryItem, FillLevel) -> Unit,
    onClearPendingItem: (SyncQueueItem) -> Unit = {},
    onClearAllPending: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val configuration = LocalConfiguration.current
    val isTablet = configuration.screenWidthDp >= 600

    if (isTablet) {
        // --- DUAL-PANE SPLIT WORKBENCH LAYOUT (TABLET / DASHBOARD) ---
        Row(
            modifier = modifier
                .fillMaxSize()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // LEFT PANE (70% Width): Pantry Inventory Workbench
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier
                    .weight(2.4f)
                    .fillMaxHeight()
            ) {
                PantryWorkbenchSection(
                    pantryItems = pantryItems,
                    onSelectItem = onSelectItem,
                    onConsume = onConsume,
                    onRestock = onRestock,
                    onUpdateFillLevel = onUpdateFillLevel
                )
            }

            // RIGHT PANE (30% Width - Compact Shopping List Intake)
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            ) {
                ShoppingIntakeSection(
                    pendingItems = pendingItems,
                    pantryItems = pantryItems,
                    pastItems = pastItems,
                    onProcessItem = onProcessItem,
                    onOpenEnrichmentModal = onOpenEnrichmentModal,
                    onClearPendingItem = onClearPendingItem,
                    onClearAllPending = onClearAllPending
                )
            }
        }
    } else {
        // --- SINGLE-PANE TABBED WORKBENCH LAYOUT (PHONE) ---
        var selectedTab by remember { mutableIntStateOf(0) }

        Column(modifier = modifier.fillMaxSize().padding(12.dp)) {
            PrimaryTabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("Stock Inventory", fontWeight = FontWeight.Bold) }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("Shopping Intake (${pendingItems.size})", fontWeight = FontWeight.Bold) }
                )
            }

            Spacer(Modifier.height(12.dp))

            if (selectedTab == 0) {
                PantryWorkbenchSection(
                    pantryItems = pantryItems,
                    onSelectItem = onSelectItem,
                    onConsume = onConsume,
                    onRestock = onRestock,
                    onUpdateFillLevel = onUpdateFillLevel
                )
            } else {
                ShoppingIntakeSection(
                    pendingItems = pendingItems,
                    pantryItems = pantryItems,
                    pastItems = pastItems,
                    onProcessItem = onProcessItem,
                    onOpenEnrichmentModal = onOpenEnrichmentModal,
                    onClearPendingItem = onClearPendingItem,
                    onClearAllPending = onClearAllPending
                )
            }
        }
    }
}

@Composable
private fun PantryWorkbenchSection(
    pantryItems: List<PantryItem>,
    onSelectItem: (PantryItem) -> Unit,
    onConsume: (PantryItem) -> Unit,
    onRestock: (PantryItem) -> Unit,
    onUpdateFillLevel: (PantryItem, FillLevel) -> Unit,
    modifier: Modifier = Modifier
) {
    var searchQuery by remember { mutableStateOf("") }
    var sortBy by remember { mutableStateOf("SHELF") }

    val filteredItems = remember(pantryItems, searchQuery, sortBy) {
        val matches = pantryItems.filter { item ->
            item.isAssigned && item.hasStock && (
                searchQuery.isBlank() ||
                item.name.contains(searchQuery, ignoreCase = true) ||
                (item.brand?.contains(searchQuery, ignoreCase = true) == true) ||
                "S${item.shelfNumber}".contains(searchQuery, ignoreCase = true) ||
                PantryConstants.getZoneLabel(item.zoneIndex).contains(searchQuery, ignoreCase = true)
            )
        }
        when (sortBy) {
            "NAME" -> matches.sortedBy { it.name }
            "STOCK" -> matches.sortedBy { it.activeCount }
            else -> matches.sortedWith(compareBy<PantryItem> { it.shelfNumber }.thenBy { it.zoneIndex }.thenBy { it.name })
        }
    }

    val groupedByShelf = remember(filteredItems) {
        filteredItems.groupBy { it.shelfNumber }
    }

    Column(modifier = modifier.padding(12.dp)) {
        // Search & Filter Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Filter stock...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search") },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface
                ),
                modifier = Modifier.weight(1f)
            )

            FilterChip(
                selected = sortBy == "SHELF",
                onClick = { sortBy = if (sortBy == "SHELF") "NAME" else "SHELF" },
                label = { Text(if (sortBy == "SHELF") "By Shelf" else "By Name") }
            )
        }

        Spacer(Modifier.height(10.dp))

        if (filteredItems.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No items match your search", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 24.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                (1..4).forEach { shelfNum ->
                    val itemsOnShelf = groupedByShelf[shelfNum] ?: emptyList()
                    if (itemsOnShelf.isNotEmpty()) {
                        item(key = "shelf_header_$shelfNum") {
                            Surface(
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "SHELF $shelfNum (${itemsOnShelf.size} items)",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                            }
                        }

                        items(itemsOnShelf, key = { it.id }) { item ->
                            WorkbenchItemRow(
                                item = item,
                                onSelectItem = onSelectItem,
                                onConsume = onConsume,
                                onRestock = onRestock,
                                onUpdateFillLevel = onUpdateFillLevel
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ShoppingIntakeSection(
    pendingItems: List<SyncQueueItem>,
    pantryItems: List<PantryItem>,
    pastItems: List<PastItem>,
    onProcessItem: (SyncQueueItem) -> Unit,
    onOpenEnrichmentModal: (SyncQueueItem) -> Unit,
    onClearPendingItem: (SyncQueueItem) -> Unit,
    onClearAllPending: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.padding(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Shopping List Intake",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${pendingItems.size} items ready to restock",
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (pendingItems.isNotEmpty()) {
                TextButton(
                    onClick = onClearAllPending,
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                ) {
                    Text("Clear Queue", color = MaterialTheme.colorScheme.error, fontSize = 11.sp)
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        if (pendingItems.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.CheckCircleOutline,
                        contentDescription = null,
                        modifier = Modifier.size(36.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "All items assigned!",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "New scans from Ingestion app appear here.",
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 24.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(pendingItems, key = { it.id.ifBlank { "${it.barcode}_${it.scannedAt}" } }) { item ->
                    val activeMatch = remember(pantryItems, item) {
                        if (item.barcode.isNotBlank()) {
                            pantryItems.find { it.barcode == item.barcode && it.isAssigned }
                        } else if (item.itemId.isNotBlank()) {
                            pantryItems.find { it.id == item.itemId && it.isAssigned }
                        } else null
                    }

                    val pastMatch = remember(pastItems, item, activeMatch) {
                        if (activeMatch == null && item.barcode.isNotBlank()) {
                            pastItems.find { it.barcode == item.barcode }
                        } else null
                    }

                    val isKnownItem = activeMatch != null || pastMatch != null
                    val shelfNumber = activeMatch?.shelfNumber ?: pastMatch?.shelfNumber

                    val locationLabel = when {
                        activeMatch != null -> "Location: Shelf ${activeMatch.shelfNumber} - ${PantryConstants.getZoneLabel(activeMatch.zoneIndex)} Zone"
                        pastMatch != null -> "Former Location: Shelf ${pastMatch.shelfNumber} - ${PantryConstants.getZoneLabel(pastMatch.zoneIndex)} Zone"
                        else -> "New Product · Unassigned"
                    }

                    val ctaLabel = when {
                        shelfNumber != null -> "1-Tap Intake to Shelf $shelfNumber"
                        else -> "Select Location & Add"
                    }

                    Card(
                        onClick = { onOpenEnrichmentModal(item) },
                        shape = RoundedCornerShape(10.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.35f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            // Top Row: Product Image + Title & Barcode + Clear Button
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                // Product Thumbnail Image
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                                        .border(
                                            1.dp,
                                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                                            RoundedCornerShape(6.dp)
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    ProductThumbnail(
                                        imageUrl = activeMatch?.imageUrl ?: pastMatch?.imageUrl ?: item.imageUrl,
                                        apiImageUrl = activeMatch?.apiImageUrl ?: pastMatch?.apiImageUrl,
                                        localImageUrl = activeMatch?.localImageUrl ?: pastMatch?.localImageUrl,
                                        localImageUri = activeMatch?.localImageUri ?: pastMatch?.localImageUri,
                                        itemName = item.productName ?: "Product",
                                        thumbnailSize = 38.dp,
                                        updatedAt = activeMatch?.updatedAt ?: pastMatch?.updatedAt ?: 0L,
                                        contentScale = ContentScale.Fit,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }

                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = item.productName ?: "Scanned Product",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f)
                                        )
                                        IconButton(
                                            onClick = { onClearPendingItem(item) },
                                            modifier = Modifier.size(20.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.Clear,
                                                contentDescription = "Clear item",
                                                tint = MaterialTheme.colorScheme.error,
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }
                                    }

                                    Text(
                                        text = "Barcode: ${item.barcode}",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            Spacer(Modifier.height(6.dp))

                            // Location Badge
                            Surface(
                                color = if (isKnownItem) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.tertiaryContainer,
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(
                                    text = locationLabel,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isKnownItem) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onTertiaryContainer,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }

                            Spacer(Modifier.height(6.dp))

                            Button(
                                onClick = { onProcessItem(item) },
                                shape = RoundedCornerShape(6.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(32.dp)
                            ) {
                                Icon(Icons.Default.AddHome, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(ctaLabel, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WorkbenchItemRow(
    item: PantryItem,
    onSelectItem: (PantryItem) -> Unit,
    onConsume: (PantryItem) -> Unit,
    onRestock: (PantryItem) -> Unit,
    onUpdateFillLevel: (PantryItem, FillLevel) -> Unit
) {
    Card(
        onClick = { onSelectItem(item) },
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .padding(8.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Location Zone Badge
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = RoundedCornerShape(6.dp)
            ) {
                Text(
                    text = "S${item.shelfNumber}-${PantryConstants.getZoneLabel(item.zoneIndex)}",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)
                )
            }

            // Product Thumbnail Image (Left of Title)
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                    .border(
                        1.dp,
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                        RoundedCornerShape(8.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                ProductThumbnail(
                    imageUrl = item.imageUrl,
                    apiImageUrl = item.apiImageUrl,
                    localImageUrl = item.localImageUrl,
                    localImageUri = item.localImageUri,
                    itemName = item.name,
                    thumbnailSize = 44.dp,
                    updatedAt = item.updatedAt,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                )
            }

            // Title & Brand Column
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${item.brand ?: "Generic"} · Stock: ${item.formattedStockText}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Maintenance Controls
            if (item.trackingType == TrackingType.BULK_LEVEL) {
                // Staples Fill Pill Button
                Surface(
                    onClick = {
                        onUpdateFillLevel(item, item.activeFill.next())
                    },
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.tertiaryContainer
                ) {
                    Text(
                        text = "Fill: ${item.activeFill.label}",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                    )
                }
            } else {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    FilledIconButton(
                        onClick = { onConsume(item) },
                        colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(Icons.Default.Remove, contentDescription = "Decrease", modifier = Modifier.size(14.dp))
                    }

                    FilledIconButton(
                        onClick = { onRestock(item) },
                        colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.primary),
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Increase", modifier = Modifier.size(14.dp))
                    }
                }
            }
        }
    }
}
