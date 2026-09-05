package com.pantry.organiser.ingestion.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import coil.compose.AsyncImage
import com.pantry.organiser.core.model.PantryConstants
import com.pantry.organiser.core.model.PantryItem
import com.pantry.organiser.ingestion.FeedbackEffect
import com.pantry.organiser.ingestion.IngestionMode
import com.pantry.organiser.ingestion.IngestionViewModel
import kotlinx.coroutines.delay

@Composable
fun IngestionScreen(
    viewModel: IngestionViewModel
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val lifecycleOwner = LocalLifecycleOwner.current

    var isGridExpanded by remember { mutableStateOf(true) }
    var selectedCell by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var selectedShelfFilter by remember { mutableStateOf<Int?>(null) }
    var selectedItem by remember { mutableStateOf<PantryItem?>(null) }

    val filteredItems = remember(selectedItem, selectedCell, selectedShelfFilter, uiState.items) {
        when {
            selectedItem != null -> {
                uiState.items.filter { it.id == selectedItem!!.id }
            }
            selectedCell != null -> {
                val cell = selectedCell!!
                val shelf = PantryConstants.rowToShelf(cell.first)
                val zone = PantryConstants.colToZone(cell.second)
                uiState.items.filter { it.shelfNumber == shelf && it.zoneIndex == zone }
            }
            selectedShelfFilter != null -> {
                uiState.items.filter { it.shelfNumber == selectedShelfFilter }
            }
            else -> uiState.items
        }
    }

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted -> hasCameraPermission = granted }
    )

    var flashColor by remember { mutableStateOf(Color.Transparent) }

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

    LaunchedEffect(Unit) {
        viewModel.effects.collect { effect ->
            flashColor = when (effect) {
                FeedbackEffect.Success -> Color.Green.copy(alpha = 0.3f)
                FeedbackEffect.Unknown -> Color.Red.copy(alpha = 0.3f)
                FeedbackEffect.Duplicate -> Color.Transparent
            }
            delay(300)
            flashColor = Color.Transparent
        }
    }

    val onCheckClick = {
        if (!hasCameraPermission) launcher.launch(Manifest.permission.CAMERA)
        else viewModel.setMode(IngestionMode.CHECK)
    }

    val onInsertClick = {
        if (!hasCameraPermission) launcher.launch(Manifest.permission.CAMERA)
        else viewModel.setMode(IngestionMode.INSERT)
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            if (uiState.mode == IngestionMode.HOME) {
                Surface(
                    tonalElevation = 8.dp,
                    shadowElevation = 8.dp,
                    modifier = Modifier.navigationBarsPadding()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedButton(
                            onClick = onCheckClick,
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Search, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Check")
                        }

                        Button(
                            onClick = onInsertClick,
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Insert / Scan")
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (uiState.mode == IngestionMode.HOME) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    // Collapsible Pantry Grid Card
                    OutlinedCard(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { isGridExpanded = !isGridExpanded },
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        "Where is the item located?",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Icon(
                                    imageVector = if (isGridExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                    contentDescription = "Toggle Grid"
                                )
                            }

                            AnimatedVisibility(visible = isGridExpanded) {
                                Column {
                                    Text(
                                        if (selectedItem != null) "Highlighted: ${selectedItem?.name} (S${selectedItem?.shelfNumber}-${PantryConstants.getZoneLabel(selectedItem?.zoneIndex ?: 0)})"
                                        else "Tap a cell to filter, or tap an item below to highlight location:",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (selectedItem != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontWeight = if (selectedItem != null) FontWeight.Bold else FontWeight.Normal,
                                        modifier = Modifier.padding(top = 4.dp, bottom = 4.dp)
                                    )
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(230.dp)
                                    ) {
                                        PantryShelfGrid(
                                            selectedCell = selectedCell,
                                            onCellClick = { row, col ->
                                                selectedItem = null
                                                selectedShelfFilter = null
                                                selectedCell = if (selectedCell == Pair(row, col)) null else Pair(row, col)
                                            },
                                            pantryItems = uiState.items,
                                            highlightedItem = selectedItem
                                        )
                                    }
                                }
                            }

                            if (!isGridExpanded) {
                                LazyRow(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 8.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    item {
                                        FilterChip(
                                            selected = selectedShelfFilter == null && selectedCell == null && selectedItem == null,
                                            onClick = {
                                                selectedShelfFilter = null
                                                selectedCell = null
                                                selectedItem = null
                                            },
                                            label = { Text("All (${uiState.items.size})") }
                                        )
                                    }
                                    items((4 downTo 1).toList()) { shelf ->
                                        val count = uiState.items.count { it.shelfNumber == shelf }
                                        val isHighlightedShelf = selectedItem?.shelfNumber == shelf
                                        FilterChip(
                                            selected = selectedShelfFilter == shelf || isHighlightedShelf,
                                            onClick = {
                                                selectedCell = null
                                                selectedItem = null
                                                selectedShelfFilter = if (selectedShelfFilter == shelf) null else shelf
                                            },
                                            label = { Text("Shelf $shelf: $count") },
                                            colors = FilterChipDefaults.filterChipColors(
                                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (selectedItem != null) {
                            InputChip(
                                selected = true,
                                onClick = { selectedItem = null },
                                label = { Text(selectedItem!!.name) },
                                leadingIcon = { Icon(Icons.Default.LocationOn, contentDescription = null, modifier = Modifier.size(16.dp)) },
                                trailingIcon = { Icon(Icons.Default.Clear, contentDescription = "Clear Highlight") }
                            )
                        } else if (selectedCell != null) {
                            val row = selectedCell!!.first
                            val col = selectedCell!!.second
                            val shelf = PantryConstants.rowToShelf(row)
                            val zoneLabel = PantryConstants.getZoneLabel(PantryConstants.colToZone(col))

                            InputChip(
                                selected = true,
                                onClick = { selectedCell = null },
                                label = { Text("Filter: Shelf $shelf-$zoneLabel (${filteredItems.size})") },
                                trailingIcon = { Icon(Icons.Default.Clear, contentDescription = "Clear") }
                            )
                        } else if (selectedShelfFilter != null) {
                            InputChip(
                                selected = true,
                                onClick = { selectedShelfFilter = null },
                                label = { Text("Filter: Shelf ${selectedShelfFilter} (${filteredItems.size})") },
                                trailingIcon = { Icon(Icons.Default.Clear, contentDescription = "Clear") }
                            )
                        } else {
                            Text(
                                "Inventory List",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(bottom = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (filteredItems.isEmpty()) {
                            item {
                                Card(
                                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                                ) {
                                    Text(
                                        "No items found for selected filter.",
                                        modifier = Modifier.padding(16.dp),
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                            }
                        }

                        items(filteredItems, key = { it.id }) { item ->
                            val isItemHighlighted = selectedItem?.id == item.id

                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        if (selectedItem?.id == item.id) {
                                            selectedItem = null
                                        } else {
                                            selectedItem = item
                                            isGridExpanded = true // Ensure shelf grid is visible
                                        }
                                    },
                                shape = RoundedCornerShape(12.dp),
                                color = if (isItemHighlighted) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                border = if (isItemHighlighted) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null
                            ) {
                                ListItem(
                                    leadingContent = {
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                                            modifier = Modifier.size(72.dp)
                                        ) {
                                            if (!item.imageUrl.isNullOrBlank()) {
                                                AsyncImage(
                                                    model = item.imageUrl,
                                                    contentDescription = item.name,
                                                    contentScale = ContentScale.Fit,
                                                    modifier = Modifier
                                                        .fillMaxSize()
                                                        .padding(4.dp)
                                                )
                                            } else {
                                                Box(
                                                    contentAlignment = Alignment.Center,
                                                    modifier = Modifier.fillMaxSize()
                                                ) {
                                                    Text(
                                                        text = item.name.take(1).uppercase(),
                                                        style = MaterialTheme.typography.titleLarge,
                                                        fontWeight = FontWeight.Bold,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                            }
                                        }
                                    },
                                    headlineContent = {
                                        Text(
                                            item.name,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isItemHighlighted) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.onSurface
                                        )
                                    },
                                    supportingContent = {
                                        Text(
                                            "${item.brand ?: "Generic"} · Shelf ${item.shelfNumber}-${PantryConstants.getZoneLabel(item.zoneIndex)}",
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    },
                                    trailingContent = {
                                        Surface(
                                            color = if (isItemHighlighted) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.primaryContainer,
                                            shape = RoundedCornerShape(8.dp)
                                        ) {
                                            Text(
                                                "Stock: ${item.formattedStockText}",
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = if (isItemHighlighted) MaterialTheme.colorScheme.onPrimary
                                                else MaterialTheme.colorScheme.onPrimaryContainer,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                            )
                                        }
                                    },
                                    colors = ListItemDefaults.colors(
                                        containerColor = Color.Transparent
                                    )
                                )
                            }
                            HorizontalDivider(modifier = Modifier.padding(horizontal = 8.dp))
                        }
                    }
                }
            }

            // SCANNER OVERLAY
            if (uiState.mode != IngestionMode.HOME) {
                Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
                    if (hasCameraPermission) {
                        AndroidView(
                            factory = { ctx ->
                                PreviewView(ctx).apply {
                                    implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                                    viewModel.startScanner(lifecycleOwner, this.surfaceProvider)
                                }
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    Box(modifier = Modifier.fillMaxSize().background(flashColor))

                    // Scanner UI (Back button, Send button, Scanned list)
                    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TextButton(onClick = { viewModel.setMode(IngestionMode.HOME) }) {
                                Text("Back", color = Color.White)
                            }
                            Spacer(modifier = Modifier.weight(1f))
                            Text(
                                if (uiState.mode == IngestionMode.INSERT) "Continuous Scan" else "Single Scan",
                                color = Color.White,
                                style = MaterialTheme.typography.titleMedium
                            )
                        }

                        Spacer(modifier = Modifier.weight(1f))

                        if (uiState.mode == IngestionMode.INSERT) {
                            Surface(
                                modifier = Modifier.fillMaxWidth().heightIn(max = 200.dp),
                                color = Color.Black.copy(alpha = 0.5f),
                                shape = MaterialTheme.shapes.medium
                            ) {
                                LazyColumn(modifier = Modifier.padding(8.dp), reverseLayout = true) {
                                    items(uiState.scannedItems) { item ->
                                        Text("Scanned: ${item.barcode}", color = Color.White)
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            Button(
                                onClick = { viewModel.sendToPantry() },
                                modifier = Modifier.fillMaxWidth().height(64.dp),
                                enabled = uiState.scannedItems.isNotEmpty() && !uiState.isSending
                            ) {
                                if (uiState.isSending) CircularProgressIndicator(color = Color.White)
                                else {
                                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null)
                                    Spacer(Modifier.width(8.dp))
                                    Text("Send to Pantry (${uiState.scannedItems.size})")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
