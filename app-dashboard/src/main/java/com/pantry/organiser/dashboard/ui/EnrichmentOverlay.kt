package com.pantry.organiser.dashboard.ui

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.pantry.organiser.core.model.FillLevel
import com.pantry.organiser.core.model.PantryConstants
import com.pantry.organiser.core.model.PantryItem
import com.pantry.organiser.dashboard.data.SyncQueueItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EnrichmentOverlay(
    syncItem: SyncQueueItem,
    existingItem: PantryItem?,
    isPastItem: Boolean = false,
    suggestedShelf: Pair<Int, Int>? = null,
    onSave: (Int, Int, Int, FillLevel) -> Unit,
    onDismiss: () -> Unit
) {
    var prototypeOption by remember { mutableIntStateOf(1) } // 1: Split View, 2: Strips, 3: Wizard
    var wizardStep by remember { mutableIntStateOf(1) } // For Option 3: Step 1 or 2

    var quantityToAdd by remember { mutableIntStateOf(1) }
    var selectedFillLevel by remember { mutableStateOf(existingItem?.activeFill ?: FillLevel.FULL) }

    val defaultRow = suggestedShelf?.first
        ?: existingItem?.shelfNumber?.let { 4 - it }
        ?: 0
    val defaultCol = suggestedShelf?.second
        ?: existingItem?.zoneIndex?.let { it - 1 }
        ?: 1

    var selectedRow by remember { mutableIntStateOf(defaultRow) }
    var selectedCol by remember { mutableIntStateOf(defaultCol) }

    val isExisting = existingItem != null && !isPastItem

    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE || configuration.screenHeightDp < 550

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.8f))
            .padding(12.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier
                .widthIn(max = if (isLandscape && prototypeOption == 1) 840.dp else 580.dp)
                .heightIn(max = if (isLandscape) 460.dp else 740.dp)
                .wrapContentHeight(),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Prototype Layout Switcher Header
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
                    shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "PROTOTYPE TESTER - SELECT LAYOUT:",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            FilterChip(
                                selected = prototypeOption == 1,
                                onClick = { prototypeOption = 1 },
                                label = { Text("1. Split View", style = MaterialTheme.typography.labelSmall) },
                                modifier = Modifier.weight(1f)
                            )
                            FilterChip(
                                selected = prototypeOption == 2,
                                onClick = { prototypeOption = 2 },
                                label = { Text("2. Strips", style = MaterialTheme.typography.labelSmall) },
                                modifier = Modifier.weight(1f)
                            )
                            FilterChip(
                                selected = prototypeOption == 3,
                                onClick = { prototypeOption = 3 },
                                label = { Text("3. Wizard", style = MaterialTheme.typography.labelSmall) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    when (prototypeOption) {
                        1 -> PrototypeOption1(
                            isLandscape = isLandscape,
                            syncItem = syncItem,
                            existingItem = existingItem,
                            isExisting = isExisting,
                            isPastItem = isPastItem,
                            quantityToAdd = quantityToAdd,
                            onQuantityChange = { quantityToAdd = it },
                            selectedRow = selectedRow,
                            selectedCol = selectedCol,
                            onCellClick = { r, c -> selectedRow = r; selectedCol = c },
                            onSave = { onSave(4 - selectedRow, selectedCol + 1, quantityToAdd, selectedFillLevel) },
                            onDismiss = onDismiss
                        )
                        2 -> PrototypeOption2(
                            syncItem = syncItem,
                            existingItem = existingItem,
                            isExisting = isExisting,
                            isPastItem = isPastItem,
                            quantityToAdd = quantityToAdd,
                            onQuantityChange = { quantityToAdd = it },
                            selectedRow = selectedRow,
                            selectedCol = selectedCol,
                            onRowSelect = { selectedRow = it },
                            onColSelect = { selectedCol = it },
                            onSave = { onSave(4 - selectedRow, selectedCol + 1, quantityToAdd, selectedFillLevel) },
                            onDismiss = onDismiss
                        )
                        3 -> PrototypeOption3(
                            step = wizardStep,
                            onStepChange = { wizardStep = it },
                            syncItem = syncItem,
                            existingItem = existingItem,
                            isExisting = isExisting,
                            isPastItem = isPastItem,
                            quantityToAdd = quantityToAdd,
                            onQuantityChange = { quantityToAdd = it },
                            selectedRow = selectedRow,
                            selectedCol = selectedCol,
                            onCellClick = { r, c -> selectedRow = r; selectedCol = c },
                            onSave = { onSave(4 - selectedRow, selectedCol + 1, quantityToAdd, selectedFillLevel) },
                            onDismiss = onDismiss
                        )
                    }
                }
            }
        }
    }
}

// PROTOTYPE OPTION 1: Two-Column Split View (Landscape) / Vertical Stack (Portrait)
@Composable
private fun PrototypeOption1(
    isLandscape: Boolean,
    syncItem: SyncQueueItem,
    existingItem: PantryItem?,
    isExisting: Boolean,
    isPastItem: Boolean,
    quantityToAdd: Int,
    onQuantityChange: (Int) -> Unit,
    selectedRow: Int,
    selectedCol: Int,
    onCellClick: (Int, Int) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit
) {
    if (isLandscape) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .weight(0.48f)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isExisting) "Restock Item" else if (isPastItem) "Re-adding Past Item" else "New Item Discovery",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                ProductCardHeader(syncItem = syncItem, existingItem = existingItem, compact = true)

                QuantitySelectorRow(quantity = quantityToAdd, onQuantityChange = onQuantityChange, compact = true)

                Button(
                    onClick = onSave,
                    modifier = Modifier.fillMaxWidth().height(44.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        if (isExisting) "Add to Pantry" else "Save to Pantry",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Column(
                modifier = Modifier.weight(0.52f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                LocationLabelText(isExisting = isExisting, isPastItem = isPastItem, existingItem = existingItem, row = selectedRow, col = selectedCol)

                Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                    PantryShelfGrid(
                        selectedCell = selectedRow to selectedCol,
                        onCellClick = onCellClick,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    } else {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isExisting) "Restock Item" else if (isPastItem) "Re-adding Past Item" else "New Item Discovery",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close")
                }
            }

            ProductCardHeader(syncItem = syncItem, existingItem = existingItem, compact = false)

            QuantitySelectorRow(quantity = quantityToAdd, onQuantityChange = onQuantityChange, compact = false)

            LocationLabelText(isExisting = isExisting, isPastItem = isPastItem, existingItem = existingItem, row = selectedRow, col = selectedCol)

            Box(modifier = Modifier.fillMaxWidth().height(200.dp)) {
                PantryShelfGrid(
                    selectedCell = selectedRow to selectedCol,
                    onCellClick = onCellClick,
                    modifier = Modifier.fillMaxSize()
                )
            }

            Button(
                onClick = onSave,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text(
                    if (isExisting) "Add to Pantry" else "Save to Pantry",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

// PROTOTYPE OPTION 2: Compact Segmented Strips
@Composable
private fun PrototypeOption2(
    syncItem: SyncQueueItem,
    existingItem: PantryItem?,
    isExisting: Boolean,
    isPastItem: Boolean,
    quantityToAdd: Int,
    onQuantityChange: (Int) -> Unit,
    selectedRow: Int,
    selectedCol: Int,
    onRowSelect: (Int) -> Unit,
    onColSelect: (Int) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Option 2: Compact Segmented Selector",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Default.Close, contentDescription = "Close")
            }
        }

        ProductCardHeader(syncItem = syncItem, existingItem = existingItem, compact = true)

        QuantitySelectorRow(quantity = quantityToAdd, onQuantityChange = onQuantityChange, compact = true)

        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

        LocationLabelText(isExisting = isExisting, isPastItem = isPastItem, existingItem = existingItem, row = selectedRow, col = selectedCol)

        // Shelf Selection Strip
        Text("Select Shelf Level:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            listOf("S4 (Top)", "S3", "S2", "S1 (Bottom)").forEachIndexed { index, label ->
                FilterChip(
                    selected = selectedRow == index,
                    onClick = { onRowSelect(index) },
                    label = { Text(label, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // Zone Selection Strip
        Text("Select Zone Area:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            listOf("Left", "Middle", "Right").forEachIndexed { index, label ->
                FilterChip(
                    selected = selectedCol == index,
                    onClick = { onColSelect(index) },
                    label = { Text(label, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Button(
            onClick = onSave,
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(
                if (isExisting) "Add to Pantry" else "Save to Pantry",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

// PROTOTYPE OPTION 3: Two-Step Interactive Wizard
@Composable
private fun PrototypeOption3(
    step: Int,
    onStepChange: (Int) -> Unit,
    syncItem: SyncQueueItem,
    existingItem: PantryItem?,
    isExisting: Boolean,
    isPastItem: Boolean,
    quantityToAdd: Int,
    onQuantityChange: (Int) -> Unit,
    selectedRow: Int,
    selectedCol: Int,
    onCellClick: (Int, Int) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Option 3: Step $step of 2 - ${if (step == 1) "Quantity" else "Shelf Location"}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Default.Close, contentDescription = "Close")
            }
        }

        LinearProgressIndicator(
            progress = { if (step == 1) 0.5f else 1.0f },
            modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp))
        )

        if (step == 1) {
            // STEP 1: Product Details & Quantity
            ProductCardHeader(syncItem = syncItem, existingItem = existingItem, compact = false)

            Text("How many did you buy?", style = MaterialTheme.typography.titleMedium)
            QuantitySelectorRow(quantity = quantityToAdd, onQuantityChange = onQuantityChange, compact = false)

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = { onStepChange(2) },
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Next: Choose Location", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(8.dp))
                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null)
            }
        } else {
            // STEP 2: Location Grid Selection
            LocationLabelText(isExisting = isExisting, isPastItem = isPastItem, existingItem = existingItem, row = selectedRow, col = selectedCol)

            Box(modifier = Modifier.fillMaxWidth().height(220.dp)) {
                PantryShelfGrid(
                    selectedCell = selectedRow to selectedCol,
                    onCellClick = onCellClick,
                    modifier = Modifier.fillMaxSize()
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = { onStepChange(1) },
                    modifier = Modifier.weight(1f).height(48.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Back")
                }

                Button(
                    onClick = onSave,
                    modifier = Modifier.weight(1f).height(48.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        if (isExisting) "Add to Pantry" else "Save to Pantry",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

// SHARED SUB-COMPONENTS
@Composable
private fun ProductCardHeader(
    syncItem: SyncQueueItem,
    existingItem: PantryItem?,
    compact: Boolean
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(if (compact) 8.dp else 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val displayImageUrl = syncItem.imageUrl ?: existingItem?.activeImageSource
            if (displayImageUrl != null) {
                AsyncImage(
                    model = displayImageUrl,
                    contentDescription = null,
                    modifier = Modifier
                        .size(if (compact) 48.dp else 64.dp)
                        .clip(RoundedCornerShape(6.dp)),
                    contentScale = ContentScale.Fit
                )
                Spacer(Modifier.width(10.dp))
            }
            Column {
                Text(
                    syncItem.productName ?: existingItem?.name ?: "Unknown Product",
                    style = if (compact) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                val brandText = syncItem.brand ?: existingItem?.brand
                if (brandText != null) {
                    Text(brandText, style = MaterialTheme.typography.bodySmall, maxLines = 1)
                }
                val quantityText = syncItem.quantity ?: existingItem?.packageQuantity
                if (quantityText != null) {
                    Text(quantityText, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@Composable
private fun QuantitySelectorRow(
    quantity: Int,
    onQuantityChange: (Int) -> Unit,
    compact: Boolean
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text("Quantity:", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(
                onClick = { if (quantity > 1) onQuantityChange(quantity - 1) },
                modifier = Modifier.size(if (compact) 36.dp else 44.dp).background(MaterialTheme.colorScheme.secondaryContainer, CircleShape)
            ) {
                Icon(Icons.Default.Remove, contentDescription = "Decrease")
            }
            Text(
                text = quantity.toString(),
                style = if (compact) MaterialTheme.typography.titleLarge else MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Black,
                modifier = Modifier.padding(horizontal = if (compact) 14.dp else 20.dp)
            )
            IconButton(
                onClick = { onQuantityChange(quantity + 1) },
                modifier = Modifier.size(if (compact) 36.dp else 44.dp).background(MaterialTheme.colorScheme.secondaryContainer, CircleShape)
            ) {
                Icon(Icons.Default.Add, contentDescription = "Increase")
            }
        }
    }
}

@Composable
private fun LocationLabelText(
    isExisting: Boolean,
    isPastItem: Boolean,
    existingItem: PantryItem?,
    row: Int,
    col: Int
) {
    val shelfNum = 4 - row
    val zoneNum = col + 1
    val currentLabel = "Selected: S$shelfNum-${PantryConstants.getZoneLabel(zoneNum)}"

    val locationLabel = when {
        isExisting && existingItem != null -> "Stored at S${existingItem.shelfNumber}-${existingItem.zoneIndex} • $currentLabel"
        isPastItem && existingItem != null -> {
            val shelfName = PantryConstants.getShelfName(existingItem.shelfNumber)
            val zoneLabel = PantryConstants.getZoneLabel(existingItem.zoneIndex)
            "Suggested: $shelfName • $zoneLabel"
        }
        else -> currentLabel
    }

    Text(
        text = locationLabel,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold
    )
}
