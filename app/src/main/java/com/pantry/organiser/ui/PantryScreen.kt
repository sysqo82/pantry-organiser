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
import android.content.res.Configuration
import kotlinx.coroutines.delay
import com.pantry.organiser.data.TrackingType
import com.pantry.organiser.ui.components.*
import com.pantry.organiser.ui.ScannerMode
import com.pantry.organiser.data.PantryItem

import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.MoreVert

@OptIn(ExperimentalMaterial3Api::class, kotlinx.coroutines.InternalCoroutinesApi::class)
@Composable
fun PantryScreen(viewModel: PantryViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val configuration = LocalConfiguration.current
    // Side-by-side layout only for wide screens in landscape
    val isTablet = configuration.screenWidthDp >= 600 && configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    // Read-Only mode for mobile devices (phones)
    val isReadOnly = configuration.screenWidthDp < 600

    // Force ViewModel into Read-Only mode if on a phone
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
                            if (isReadOnly) {
                                IconButton(onClick = { viewModel.showScanner(ScannerMode.RESTOCK) }) {
                                    Icon(Icons.Default.Search, contentDescription = "Lookup Item")
                                }
                            }
                        }
                    )
                }
            },
            floatingActionButton = {
                // Hide FAB on tablets (since they have the Action Dock) and on mobile Read-Only mode
                if (!isTablet && !isReadOnly) {
                    FloatingActionButton(
                        onClick = { viewModel.showScanner(ScannerMode.RESTOCK) },
                        modifier = Modifier.navigationBarsPadding()
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Add Item")
                    }
                }
            }
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
                onCancelConsume = { viewModel.cancelPendingConsume() },
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
            modifier = Modifier.weight(1f)
        )
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
            Text(
                text = "Pantry Organiser",
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Black,
                modifier = Modifier.padding(bottom = 24.dp)
            )

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
