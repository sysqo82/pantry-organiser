package com.pantry.organiser.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowCompat
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import android.app.Activity
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import androidx.compose.ui.res.stringResource
import com.pantry.organiser.R
import kotlinx.coroutines.delay
import com.pantry.organiser.core.model.TrackingType
import com.pantry.organiser.ui.components.*
import com.pantry.organiser.ui.ScannerMode
import com.pantry.organiser.core.model.PantryItem

import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.MoreVert
import com.pantry.organiser.ui.VisualSearchScreen
import com.pantry.organiser.ui.ItemDetailActionModal
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class, kotlinx.coroutines.InternalCoroutinesApi::class)
@Composable
fun PantryScreen(viewModel: PantryViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val configuration = LocalConfiguration.current
    
    // 1. Determine Device Role from Configuration Resource
    val deviceRole = stringResource(R.string.device_role)
    val isPrimaryDevice = deviceRole == "PRIMARY"
    
    // 2. Adaptive Layout Detection
    // We use smallestScreenWidthDp to differentiate between a tablet and a phone in landscape.
    // Phones in landscape typically have width >= 600 but smallest width < 600.
    val isTablet = configuration.smallestScreenWidthDp >= 600
    
    // Tablet is always Admin/Write. Mobile respects the device_role.
    val isReadOnly = if (isTablet) false else !isPrimaryDevice

    // 3. Optional Orientation Handling
    // We ensure TabletLayout handles landscape correctly by being the active layout.
    // No explicit orientation lock needed if we want to allow flexibility,
    // but the layout will adapt based on the width.

    // Force ViewModel into Read-Only mode based on role

    LaunchedEffect(isReadOnly) {
        viewModel.setReadOnly(isReadOnly)
    }

    val listState = rememberLazyListState()
    val snackbarHostState = remember { SnackbarHostState() }

    // Step 1-3 Coordinated Transition Sequence for Existing Items
    LaunchedEffect(uiState.recognizedItem) {
        uiState.recognizedItem?.let { item ->
            // Step 1: Hold for visual recognition confirmation (viewfinder flash)
            delay(250)
            
            // Step 2: Dismiss Scanner
            viewModel.hideScanner()
            
            // Step 3: Main Screen Focus & Highlight (Only for Restock/Add)
            if (uiState.scannerMode == ScannerMode.RESTOCK) {
                viewModel.selectItem(item)
                val index = uiState.filteredItems.indexOfFirst { it.id == item.id }
                if (index != -1) {
                    listState.animateScrollToItem(index)
                }
                
                // Step 4: Temporary Highlight Pulse (Ease back after 1 second)
                delay(1000)
                if (uiState.highlightedItemId == item.id) {
                    viewModel.selectItem(item) // Toggle off
                }
            }
        }
    }

    // Handle user notifications (e.g., auto-rollover message)
    LaunchedEffect(uiState.userNotification) {
        uiState.userNotification?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.clearNotification()
        }
    }

    // Auto-remove highlight after 10 seconds
    LaunchedEffect(uiState.highlightedItemId) {
        if (uiState.highlightedItemId != null) {
            delay(10000)
            viewModel.clearHighlight()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                if (!isTablet) {
                    TopAppBar(
                        title = { Text("Pantry Organiser") },
                        windowInsets = WindowInsets.statusBars,
                        actions = {
                            IconButton(onClick = { viewModel.showScanner(ScannerMode.RESTOCK) }) {
                                Icon(Icons.Default.Search, contentDescription = "Lookup Item")
                            }
                        }
                    )
                }

            },
            floatingActionButton = {}

        ) { innerPadding ->
            if (isTablet) {
                TabletLayout(
                    innerPadding = innerPadding,
                    uiState = uiState,
                    viewModel = viewModel,
                    listState = listState
                )
            } else {
                MobileLayout(
                    padding = innerPadding,
                    isReadOnly = isReadOnly,
                    uiState = uiState,
                    viewModel = viewModel,
                    listState = listState
                )
            }

            uiState.editingItem?.let { item ->
                EditItemBottomSheet(
                    item = item,
                    isReadOnly = isReadOnly,
                    onDismiss = { viewModel.clearEditingItem() },
                    onSave = { viewModel.updateItem(it) },
                    onDelete = { viewModel.deleteItem(it) },
                    onRestoreApiImage = { viewModel.restoreApiImage(item) }
                )
            }

            uiState.photoCaptureItem?.let { item ->
                PhotoCaptureBottomSheet(
                    isTablet = isTablet,
                    onPhotoCaptured = { path ->
                        viewModel.updateLocalImageUri(item, path)
                    },
                    onDismiss = { viewModel.cancelPhotoCapture() }
                )
            }
        }

        // VISUAL SEARCH OVERLAY (Tablet Only)
        if (isTablet) {
            var interactionKey by remember { mutableStateOf(0) }
            
            LaunchedEffect(uiState.isVisualSearchVisible, interactionKey) {
                if (uiState.isVisualSearchVisible) {
                    delay(30000)
                    viewModel.hideVisualSearch()
                }
            }

            AnimatedVisibility(
                visible = uiState.isVisualSearchVisible,
                enter = fadeIn(tween(300)) + slideInVertically(initialOffsetY = { it / 2 }),
                exit = fadeOut(tween(300)) + slideOutVertically(targetOffsetY = { it / 2 })
            ) {
                VisualSearchScreen(
                    items = uiState.items,
                    onItemClick = { 
                        interactionKey++
                        viewModel.selectVisualSearchItem(it) 
                    },
                    onDismiss = { viewModel.hideVisualSearch() },
                    onInteraction = { interactionKey++ }
                )
            }
            
            uiState.visualSearchSelectedItem?.let { item ->
                ItemDetailActionModal(
                    item = item,
                    onConsume = { 
                        interactionKey++
                        viewModel.consumeUnits(item, it)
                        viewModel.hideVisualSearch()
                    },
                    onRestock = { 
                        interactionKey++
                        viewModel.addSealedUnit(item)
                        viewModel.hideVisualSearch()
                    },
                    onEdit = {
                        interactionKey++
                        viewModel.hideVisualSearch()
                        viewModel.startEditItem(item)
                    },
                    onUpdateLevel = { level ->
                        interactionKey++
                        viewModel.updateItemFillLevel(item, level)
                        viewModel.hideVisualSearch()
                    },
                    onDismiss = { viewModel.clearVisualSearchItem() },
                    onInteraction = { interactionKey++ }
                )
            }
        }

        // TRUE FULL-SCREEN SCANNER OVERLAY
        AnimatedVisibility(
            visible = uiState.isScannerVisible,
            enter = fadeIn(tween(250)) + slideInVertically(
                initialOffsetY = { it },
                animationSpec = tween(250)
            ),
            exit = fadeOut(tween(200)) + slideOutVertically(
                targetOffsetY = { it },
                animationSpec = tween(200)
            )
        ) {
            val context = LocalContext.current
            
            // Re-enforce immersive mode when scanner is open
            DisposableEffect(Unit) {
                if (isTablet) {
                    (context as? Activity)?.window?.let { window ->
                        val controller = WindowCompat.getInsetsController(window, window.decorView)
                        controller.hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
                    }
                }
                onDispose {}
            }

            ScannerView(
                scannedProduct = uiState.scannedProduct,
                isLoading = uiState.isLoading,
                error = uiState.error,
                recognizedItem = uiState.recognizedItem,
                pendingNewItem = uiState.pendingNewItem,
                pendingConsumeItem = uiState.pendingConsumeItem,
                scannerMode = uiState.scannerMode,
                onBarcodeScanned = { viewModel.scanBarcode(it) },
                onManualEntry = { viewModel.startManualEntry() },
                onAssignShelf = { r, c -> viewModel.assignPendingItemShelf(r, c) },
                onCancelPending = { viewModel.cancelPendingItem() },
                onUpdateConsumeLevel = { viewModel.updatePendingConsumeLevel(it) },
                onUpdateConsumeUnits = { viewModel.updatePendingConsumeUnits(it) },
                onCancelConsume = { viewModel.cancelPendingConsume() },
                isReadOnly = isReadOnly,
                isTablet = isTablet,
                onSave = { name, brand, qty, r, c, img, bc, type ->


                    viewModel.saveScannedItem(name, brand, qty, r, c, img, bc, type)
                },
                onDismiss = { 
                    viewModel.hideScanner()
                }
            )
        }
    }
}



@Composable
fun MobileLayout(
    padding: PaddingValues,
    isReadOnly: Boolean,
    uiState: PantryUiState,
    viewModel: PantryViewModel,
    listState: LazyListState
) {
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    if (isLandscape) {
        Row(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            PantryShelfGrid(
                selectedCell = uiState.selectedShelf,
                onCellClick = { r, c -> viewModel.selectShelf(r, c) },
                highlightedItem = uiState.items.find { it.id == uiState.highlightedItemId },
                modifier = Modifier
                    .weight(0.4f)
                    .fillMaxHeight()
            )

            VerticalDivider(modifier = Modifier.fillMaxHeight().width(1.dp), color = MaterialTheme.colorScheme.outlineVariant)

            PantryItemList(
                filteredItems = uiState.filteredItems,
                highlightedItemId = uiState.highlightedItemId,
                isReadOnly = isReadOnly,
                listState = listState,
                viewModel = viewModel,
                contentPadding = PaddingValues(top = 8.dp, bottom = 16.dp),
                modifier = Modifier.weight(0.6f).fillMaxHeight()
            )
        }
    } else {
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            PantryShelfGrid(
                selectedCell = uiState.selectedShelf,
                onCellClick = { r, c -> viewModel.selectShelf(r, c) },
                highlightedItem = uiState.items.find { it.id == uiState.highlightedItemId },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.4f)
            )

            HorizontalDivider()

            PantryItemList(
                filteredItems = uiState.filteredItems,
                highlightedItemId = uiState.highlightedItemId,
                isReadOnly = isReadOnly,
                listState = listState,
                viewModel = viewModel,
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
                modifier = Modifier.weight(0.6f)
            )
        }
    }
}


@Composable
fun TabletLayout(
    innerPadding: PaddingValues,
    uiState: PantryUiState,
    viewModel: PantryViewModel,
    listState: LazyListState
) {
    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        // Left Pane (40% width) - Title and Grid
        Column(
            modifier = Modifier
                .weight(0.4f)
                .fillMaxHeight()
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Pantry Organiser",
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Black
                )
                
                IconButton(
                    onClick = { viewModel.showVisualSearch() },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Visual Search",
                        modifier = Modifier.size(32.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            PantryShelfGrid(
                selectedCell = uiState.selectedShelf,
                onCellClick = { r, c -> viewModel.selectShelf(r, c) },
                highlightedItem = uiState.items.find { it.id == uiState.highlightedItemId },
                modifier = Modifier.fillMaxWidth()
            )
        }

        VerticalDivider(modifier = Modifier.fillMaxHeight().width(1.dp), color = MaterialTheme.colorScheme.outlineVariant)

        // Right Pane (60% width) - Item List and Action Buttons at the bottom
        Column(
            modifier = Modifier
                .weight(0.6f)
                .fillMaxHeight()
        ) {
            // Item List (Takes remaining space)
            Box(modifier = Modifier.weight(1f)) {
                PantryItemList(
                    filteredItems = uiState.filteredItems,
                    highlightedItemId = uiState.highlightedItemId,
                    isReadOnly = false, // Tablet is always Admin
                    listState = listState,
                    viewModel = viewModel,
                    contentPadding = PaddingValues(top = 8.dp, bottom = 16.dp, start = 8.dp, end = 8.dp),
                    modifier = Modifier.fillMaxSize()
                )
            }

            HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(modifier = Modifier.height(16.dp))

            // Action Buttons pinned below the list
            ActionDock(
                onAddClick = { viewModel.showScanner(ScannerMode.RESTOCK) },
                onConsumeClick = { viewModel.showScanner(ScannerMode.CONSUME) }
            )
        }
    }
}

@Composable
fun ActionDock(
    onAddClick: () -> Unit,
    onConsumeClick: () -> Unit
) {
    val configuration = LocalConfiguration.current
    val isCompact = configuration.screenHeightDp < 500

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = if (isCompact) 8.dp else 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        DockButton(
            title = "Add / Restock",
            subtitle = if (isCompact) "" else "Scan groceries",
            icon = Icons.Default.Add,
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            onClick = onAddClick,
            modifier = Modifier.weight(1f),
            height = if (isCompact) 80.dp else 120.dp
        )
        
        DockButton(
            title = "Take / Consume",
            subtitle = if (isCompact) "" else "Quick scan out",
            icon = Icons.Default.Remove,
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
            onClick = onConsumeClick,
            modifier = Modifier.weight(1f),
            height = if (isCompact) 80.dp else 120.dp
        )
    }
}

@Composable
fun DockButton(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    containerColor: Color,
    contentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    height: androidx.compose.ui.unit.Dp = 144.dp
) {
    Surface(
        onClick = onClick,
        color = containerColor,
        contentColor = contentColor,
        shape = RoundedCornerShape(16.dp),
        tonalElevation = 2.dp,
        modifier = modifier.height(height)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 16.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(if (subtitle.isEmpty()) 24.dp else 32.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column(verticalArrangement = Arrangement.Center) {
                Text(
                    text = title,
                    style = if (subtitle.isEmpty()) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                if (subtitle.isNotEmpty()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = contentColor.copy(alpha = 0.7f)
                    )
                }
            }
        }
    }
}

@Composable
fun PantryItemList(
    filteredItems: List<PantryItem>,
    highlightedItemId: String?,
    isReadOnly: Boolean,
    listState: LazyListState,
    viewModel: PantryViewModel,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp)
) {
    if (filteredItems.isEmpty()) {
        Box(modifier = modifier.fillMaxWidth().fillMaxHeight(), contentAlignment = Alignment.Center) {
            Text("No items in this zone", style = MaterialTheme.typography.bodyLarge)
        }
    } else {
        LazyColumn(
            state = listState,
            modifier = modifier.fillMaxWidth(),
            contentPadding = contentPadding,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(filteredItems, key = { it.id }) { item ->
                PantryItemCard(
                    item = item,
                    isHighlighted = highlightedItemId == item.id,
                    isReadOnly = isReadOnly,
                    onConsume = { viewModel.consumePortion(item) },
                    onAddSealed = { viewModel.addSealedUnit(item) },
                    onRemoveSealed = { viewModel.removeSealedUnit(item) },
                    onIncrementFill = { viewModel.incrementActiveFill(item) },
                    onClick = { viewModel.selectItem(item) },
                    onLongClick = { viewModel.startEditItem(item) },
                    onEditClick = { viewModel.startEditItem(item) },
                    onPhotoRequest = { viewModel.startPhotoCapture(item) }
                )
            }
        }
    }
}
