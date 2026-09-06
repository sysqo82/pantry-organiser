package com.pantry.organiser.dashboard.ui

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
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
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier
                .widthIn(max = if (isLandscape) 820.dp else 560.dp)
                .heightIn(max = if (isLandscape) 440.dp else 720.dp)
                .wrapContentHeight(),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            if (isLandscape) {
                // LANDSCAPE: Side-by-side 2-column layout
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    // Left Column: Product Header, Quantity Controls, Save Action
                    Column(
                        modifier = Modifier
                            .weight(0.48f)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Title + Close button
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
                            IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                                Icon(Icons.Default.Close, contentDescription = "Close")
                            }
                        }

                        // Product Card (Compact)
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val displayImageUrl = syncItem.imageUrl ?: existingItem?.activeImageSource
                                if (displayImageUrl != null) {
                                    AsyncImage(
                                        model = displayImageUrl,
                                        contentDescription = null,
                                        modifier = Modifier
                                            .size(52.dp)
                                            .clip(RoundedCornerShape(6.dp)),
                                        contentScale = ContentScale.Fit
                                    )
                                    Spacer(Modifier.width(10.dp))
                                }
                                Column {
                                    Text(
                                        syncItem.productName ?: existingItem?.name ?: "Unknown Product",
                                        style = MaterialTheme.typography.bodyMedium,
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

                        // Quantity Selector
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                "Quantity:",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(
                                    onClick = { if (quantityToAdd > 1) quantityToAdd-- },
                                    modifier = Modifier.size(40.dp).background(MaterialTheme.colorScheme.secondaryContainer, CircleShape)
                                ) {
                                    Icon(Icons.Default.Remove, contentDescription = "Decrease")
                                }
                                Text(
                                    text = quantityToAdd.toString(),
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Black,
                                    modifier = Modifier.padding(horizontal = 16.dp)
                                )
                                IconButton(
                                    onClick = { quantityToAdd++ },
                                    modifier = Modifier.size(40.dp).background(MaterialTheme.colorScheme.secondaryContainer, CircleShape)
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = "Increase")
                                }
                            }
                        }

                        Spacer(Modifier.weight(1f, fill = false))

                        // Save Button
                        Button(
                            onClick = {
                                val shelf = 4 - selectedRow
                                val zone = selectedCol + 1
                                onSave(shelf, zone, quantityToAdd, selectedFillLevel)
                            },
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

                    // Right Column: Location Header + Full Height Grid
                    Column(
                        modifier = Modifier.weight(0.52f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val locationLabel = when {
                            isExisting && existingItem != null -> "Stored at S${existingItem.shelfNumber}-${existingItem.zoneIndex}"
                            isPastItem && existingItem != null -> {
                                val shelfName = PantryConstants.getShelfName(existingItem.shelfNumber)
                                val zoneLabel = PantryConstants.getZoneLabel(existingItem.zoneIndex)
                                "Suggested: $shelfName • $zoneLabel"
                            }
                            else -> "Assign a shelf"
                        }

                        Text(
                            text = locationLabel,
                            style = MaterialTheme.typography.titleSmall,
                            color = if (isExisting || isPastItem) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Bold
                        )

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                        ) {
                            PantryShelfGrid(
                                selectedCell = selectedRow to selectedCol,
                                onCellClick = { r, c ->
                                    selectedRow = r
                                    selectedCol = c
                                },
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }
            } else {
                // PORTRAIT: Compact single-column vertical flow
                Column(
                    modifier = Modifier
                        .padding(20.dp)
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (isExisting) "Restock Item" else if (isPastItem) "Re-adding Past Item" else "New Item Discovery",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                        )
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, contentDescription = "Close")
                        }
                    }

                    // Product Card
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val displayImageUrl = syncItem.imageUrl ?: existingItem?.activeImageSource
                            if (displayImageUrl != null) {
                                AsyncImage(
                                    model = displayImageUrl,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(64.dp)
                                        .clip(RoundedCornerShape(8.dp)),
                                    contentScale = ContentScale.Fit
                                )
                                Spacer(Modifier.width(12.dp))
                            }
                            Column {
                                Text(
                                    syncItem.productName ?: existingItem?.name ?: "Unknown Product",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                val brandText = syncItem.brand ?: existingItem?.brand
                                if (brandText != null) {
                                    Text(brandText, style = MaterialTheme.typography.bodySmall)
                                }
                                val quantityText = syncItem.quantity ?: existingItem?.packageQuantity
                                if (quantityText != null) {
                                    Text(quantityText, style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }

                    // Quantity Selector
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("How many did you buy?", style = MaterialTheme.typography.titleMedium)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = { if (quantityToAdd > 1) quantityToAdd-- },
                                modifier = Modifier.size(48.dp).background(MaterialTheme.colorScheme.secondaryContainer, CircleShape)
                            ) {
                                Icon(Icons.Default.Remove, contentDescription = "Decrease")
                            }
                            Text(
                                text = quantityToAdd.toString(),
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Black,
                                modifier = Modifier.padding(horizontal = 20.dp)
                            )
                            IconButton(
                                onClick = { quantityToAdd++ },
                                modifier = Modifier.size(48.dp).background(MaterialTheme.colorScheme.secondaryContainer, CircleShape)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = "Increase")
                            }
                        }
                    }

                    val locationLabel = when {
                        isExisting && existingItem != null -> "Stored at S${existingItem.shelfNumber}-${existingItem.zoneIndex}"
                        isPastItem && existingItem != null -> {
                            val shelfName = PantryConstants.getShelfName(existingItem.shelfNumber)
                            val zoneLabel = PantryConstants.getZoneLabel(existingItem.zoneIndex)
                            "Suggested Location: $shelfName • $zoneLabel (Last location)"
                        }
                        else -> "Assign a shelf"
                    }

                    Text(
                        text = locationLabel,
                        style = MaterialTheme.typography.titleMedium,
                        color = if (isExisting || isPastItem) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        fontWeight = if (isPastItem) FontWeight.Bold else FontWeight.Normal
                    )

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                    ) {
                        PantryShelfGrid(
                            selectedCell = selectedRow to selectedCol,
                            onCellClick = { r, c ->
                                selectedRow = r
                                selectedCol = c
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    Button(
                        onClick = {
                            val shelf = 4 - selectedRow
                            val zone = selectedCol + 1
                            onSave(shelf, zone, quantityToAdd, selectedFillLevel)
                        },
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        shape = RoundedCornerShape(16.dp)
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
    }
}
