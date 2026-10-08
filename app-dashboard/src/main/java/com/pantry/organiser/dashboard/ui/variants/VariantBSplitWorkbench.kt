package com.pantry.organiser.dashboard.ui.variants

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pantry.organiser.core.model.FillLevel
import com.pantry.organiser.core.model.PantryConstants
import com.pantry.organiser.core.model.PantryItem
import com.pantry.organiser.core.model.TrackingType
import com.pantry.organiser.dashboard.data.SyncQueueItem

/**
 * PROTOTYPE VARIANT B: Split-Screen Workbench
 * UX Focus: Side-by-side split screen for rapid pantry maintenance on the left,
 * and a dedicated Shopping List Intake Station with 1-tap smart location suggestions on the right.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VariantBSplitWorkbench(
    pantryItems: List<PantryItem>,
    pendingItems: List<SyncQueueItem>,
    onProcessItem: (SyncQueueItem) -> Unit,
    onSelectItem: (PantryItem) -> Unit,
    onConsume: (PantryItem) -> Unit,
    onRestock: (PantryItem) -> Unit,
    onUpdateFillLevel: (PantryItem, FillLevel) -> Unit,
    onClearAllPending: () -> Unit,
    modifier: Modifier = Modifier
) {
    var searchQuery by remember { mutableStateOf("") }
    var sortBy by remember { mutableStateOf("SHELF") } // SHELF, NAME, STOCK

    val filteredItems = remember(pantryItems, searchQuery, sortBy) {
        val matches = pantryItems.filter { item ->
            item.isAssigned && item.hasStock && (
                searchQuery.isBlank() ||
                item.name.contains(searchQuery, ignoreCase = true) ||
                (item.brand?.contains(searchQuery, ignoreCase = true) == true) ||
                "S${item.shelfNumber}".contains(searchQuery, ignoreCase = true)
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

    Row(
        modifier = modifier
            .fillMaxSize()
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // --- LEFT PANE (60% Width): PANTRY INVENTORY WORKBENCH ---
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier
                .weight(1.4f)
                .fillMaxHeight()
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                // Header & Search
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
                        modifier = Modifier.weight(1f)
                    )

                    // Sort Filter
                    FilterChip(
                        selected = sortBy == "SHELF",
                        onClick = { sortBy = if (sortBy == "SHELF") "NAME" else "SHELF" },
                        label = { Text(if (sortBy == "SHELF") "By Shelf" else "By Name") }
                    )
                }

                Spacer(Modifier.height(10.dp))

                // Inventory Items Grouped by Shelf
                if (filteredItems.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No stock items match your search", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(bottom = 60.dp),
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

        // --- RIGHT PANE (40% Width): SHOPPING LIST INTAKE STATION ---
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Shopping List Intake",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${pendingItems.size} items ready to restock",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    if (pendingItems.isNotEmpty()) {
                        TextButton(onClick = onClearAllPending) {
                            Text("Clear", color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                        }
                    }
                }

                Spacer(Modifier.height(10.dp))

                if (pendingItems.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.CheckCircleOutline,
                                contentDescription = null,
                                modifier = Modifier.size(40.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = "All shopping items assigned!",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "New scans from Ingestion mobile device will appear here instantly.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(bottom = 60.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(pendingItems, key = { it.id.ifBlank { "${it.barcode}_${it.scannedAt}" } }) { item ->
                            Card(
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = item.productName ?: "Scanned Product",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f)
                                        )
                                    }

                                    Text(
                                        text = "Barcode: ${item.barcode}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )

                                    Spacer(Modifier.height(6.dp))

                                    // Smart Location Suggestion
                                    Surface(
                                        color = MaterialTheme.colorScheme.primaryContainer,
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Text(
                                            text = "Suggested: Shelf 2 - Mid Zone",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }

                                    Spacer(Modifier.height(8.dp))

                                    Button(
                                        onClick = { onProcessItem(item) },
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Icon(Icons.Default.AddHome, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text("1-Tap Intake to Shelf")
                                    }
                                }
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
            // Zone badge
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

            // Name & Quantity
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
