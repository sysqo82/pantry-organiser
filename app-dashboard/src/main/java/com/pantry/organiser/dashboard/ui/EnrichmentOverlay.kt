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
import com.pantry.organiser.core.model.FillLevel
import com.pantry.organiser.core.model.PantryConstants
import com.pantry.organiser.core.model.PantryItem
import com.pantry.organiser.dashboard.data.SyncQueueItem
import com.pantry.organiser.dashboard.ui.components.ProductThumbnail

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
    var selectedFillLevel by remember { mutableStateOf(if (isPastItem) FillLevel.FULL else (existingItem?.activeFill ?: FillLevel.FULL)) }

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
                .widthIn(max = if (isLandscape) 840.dp else 560.dp)
                .heightIn(max = if (isLandscape) 460.dp else 720.dp)
                .wrapContentHeight(),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Header (Title + Close Button)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isExisting) "Restock Item" else if (isPastItem) "Re-adding Past Item" else "New Item Discovery",
                        style = if (isLandscape) MaterialTheme.typography.titleMedium else MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                if (isLandscape) {
                    // LANDSCAPE: 2-Column Side-by-Side Content Area
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f, fill = false),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Left Column: Product Info Card (fills remaining top height) & Quantity Selector (Bottom, aligned with S1 row)
                        Column(
                            modifier = Modifier
                                .weight(0.46f)
                                .height(242.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            ProductCardHeader(
                                syncItem = syncItem, 
                                existingItem = existingItem, 
                                compact = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f)
                            )
                            QuantitySelectorRow(quantity = quantityToAdd, onQuantityChange = { quantityToAdd = it }, compact = true)
                        }

                        // Right Column: Location Header & Shelf Grid
                        Column(
                            modifier = Modifier
                                .weight(0.54f)
                                .height(242.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            LocationLabelText(
                                isExisting = isExisting,
                                isPastItem = isPastItem,
                                existingItem = existingItem,
                                row = selectedRow,
                                col = selectedCol
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
                    // PORTRAIT: Single-Column Vertical Content Area
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState()),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        ProductCardHeader(syncItem = syncItem, existingItem = existingItem, compact = false)

                        QuantitySelectorRow(quantity = quantityToAdd, onQuantityChange = { quantityToAdd = it }, compact = false)

                        LocationLabelText(
                            isExisting = isExisting,
                            isPastItem = isPastItem,
                            existingItem = existingItem,
                            row = selectedRow,
                            col = selectedCol
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
                    }
                }

                // Full-Width Primary Action Button at the Bottom
                Button(
                    onClick = {
                        val shelf = 4 - selectedRow
                        val zone = selectedCol + 1
                        onSave(shelf, zone, quantityToAdd, selectedFillLevel)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(14.dp)
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
    compact: Boolean,
    modifier: Modifier = Modifier
) {
    val productName = syncItem.productName?.takeIf { it.isNotBlank() }
        ?: existingItem?.name
        ?: "Unknown Product"
    val brandText = syncItem.brand?.takeIf { it.isNotBlank() }
        ?: existingItem?.brand
    val quantityText = syncItem.quantity?.takeIf { it.isNotBlank() }
        ?: existingItem?.packageQuantity

    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        shape = RoundedCornerShape(16.dp),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(if (compact) 12.dp else 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ProductThumbnail(
                imageUrl = syncItem.imageUrl?.takeIf { it.isNotBlank() } ?: existingItem?.imageUrl,
                apiImageUrl = existingItem?.apiImageUrl,
                localImageUrl = existingItem?.localImageUrl,
                localImageUri = existingItem?.localImageUri,
                itemName = productName,
                thumbnailSize = if (compact) null else 140.dp,
                updatedAt = existingItem?.updatedAt ?: 0L,
                contentScale = ContentScale.Fit,
                modifier = if (compact) {
                    Modifier
                        .fillMaxHeight()
                        .aspectRatio(0.85f)
                        .clip(RoundedCornerShape(12.dp))
                } else {
                    Modifier.clip(RoundedCornerShape(12.dp))
                }
            )
            Spacer(Modifier.width(16.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = productName,
                    style = if (compact) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
                if (!brandText.isNullOrBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = brandText,
                        style = if (compact) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (!quantityText.isNullOrBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = quantityText,
                        style = if (compact) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
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
    val buttonSize = if (compact) 38.dp else 44.dp
    val iconSize = if (compact) 20.dp else 24.dp

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text("Quantity:", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Surface(
                onClick = { if (quantity > 1) onQuantityChange(quantity - 1) },
                shape = CircleShape,
                color = MaterialTheme.colorScheme.secondaryContainer,
                modifier = Modifier.size(buttonSize)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.Remove,
                        contentDescription = "Decrease",
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(iconSize)
                    )
                }
            }

            Text(
                text = quantity.toString(),
                style = if (compact) MaterialTheme.typography.titleLarge else MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Black
            )

            Surface(
                onClick = { onQuantityChange(quantity + 1) },
                shape = CircleShape,
                color = MaterialTheme.colorScheme.secondaryContainer,
                modifier = Modifier.size(buttonSize)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = "Increase",
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(iconSize)
                    )
                }
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
