package com.pantry.organiser.dashboard.ui.variants

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
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
 * PROTOTYPE VARIANT C: Zone Tactical Map
 * UX Focus: Visual spatial representation of the physical 4-shelf pantry structure.
 * Searching highlights matching physical shelf zones instantly.
 * Bottom express dock provides fast 1-tap shopping list intake into any selected zone.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VariantCZoneTacticalMap(
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
    var selectedZoneForFilter by remember { mutableStateOf<Pair<Int, Int>?>(null) } // shelfNumber (1..4) to zoneIndex (1..3)

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(10.dp)
    ) {
        // --- 1. TACTICAL SEARCH & MAP FILTER HEADER ---
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .padding(10.dp)
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search to highlight physical shelf location...") },
                    leadingIcon = { Icon(Icons.Default.LocationOn, contentDescription = "Search", tint = MaterialTheme.colorScheme.primary) },
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

                if (selectedZoneForFilter != null) {
                    FilterChip(
                        selected = true,
                        onClick = { selectedZoneForFilter = null },
                        label = { Text("Filtered: S${selectedZoneForFilter?.first}-${PantryConstants.getZoneLabel(selectedZoneForFilter?.second ?: 1)}") },
                        trailingIcon = { Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(14.dp)) }
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        // --- 2. VISUAL 4-SHELF PHYSICAL MATRIX ---
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            items((1..4).toList(), key = { "shelf_$it" }) { shelfNumber ->
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 4.dp, vertical = 2.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "SHELF $shelfNumber",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        Spacer(Modifier.height(4.dp))

                        // 3 Zones per shelf (1: Left, 2: Mid, 3: Right)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            (1..3).forEach { zoneIndex ->
                                val zoneItems = pantryItems.filter {
                                    it.isAssigned && it.hasStock && it.shelfNumber == shelfNumber && it.zoneIndex == zoneIndex
                                }

                                val hasMatchingSearch = searchQuery.isNotBlank() && zoneItems.any { item ->
                                    item.name.contains(searchQuery, ignoreCase = true) ||
                                            (item.brand?.contains(searchQuery, ignoreCase = true) == true)
                                }

                                val isSelectedZone = selectedZoneForFilter?.first == shelfNumber && selectedZoneForFilter?.second == zoneIndex

                                val borderColor by animateColorAsState(
                                    targetValue = when {
                                        hasMatchingSearch -> MaterialTheme.colorScheme.primary
                                        isSelectedZone -> MaterialTheme.colorScheme.tertiary
                                        else -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                                    },
                                    label = "zoneBorder"
                                )

                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (hasMatchingSearch)
                                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                                    else if (isSelectedZone)
                                        MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.3f)
                                    else
                                        MaterialTheme.colorScheme.surface,
                                    border = BorderStroke(if (hasMatchingSearch || isSelectedZone) 2.dp else 1.dp, borderColor),
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable {
                                            selectedZoneForFilter = if (isSelectedZone) null else (shelfNumber to zoneIndex)
                                        }
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .padding(6.dp)
                                            .fillMaxWidth()
                                    ) {
                                        Text(
                                            text = "Zone ${PantryConstants.getZoneLabel(zoneIndex)} (${zoneItems.size})",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )

                                        Spacer(Modifier.height(4.dp))

                                        if (zoneItems.isEmpty()) {
                                            Text(
                                                text = "Empty",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontSize = 9.sp,
                                                color = MaterialTheme.colorScheme.outline,
                                                modifier = Modifier.padding(vertical = 4.dp)
                                            )
                                        } else {
                                            zoneItems.take(3).forEach { item ->
                                                TacticalItemBadge(
                                                    item = item,
                                                    isHighlighted = searchQuery.isNotBlank() && item.name.contains(searchQuery, ignoreCase = true),
                                                    onSelectItem = onSelectItem,
                                                    onConsume = onConsume,
                                                    onRestock = onRestock,
                                                    onUpdateFillLevel = onUpdateFillLevel
                                                )
                                                Spacer(Modifier.height(2.dp))
                                            }
                                            if (zoneItems.size > 3) {
                                                Text(
                                                    text = "+${zoneItems.size - 3} more",
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
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

        // --- 3. BOTTOM EXPRESS INTAKE DOCK ---
        if (pendingItems.isNotEmpty()) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                shadowElevation = 4.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
            ) {
                Row(
                    modifier = Modifier
                        .padding(10.dp)
                        .fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Shopping Intake Express Dock",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            text = "${pendingItems.size} items scanned. Tap item to place in selected zone.",
                            style = MaterialTheme.typography.bodySmall,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                        )
                    }

                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.weight(1.5f)
                    ) {
                        items(pendingItems) { item ->
                            Button(
                                onClick = { onProcessItem(item) },
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                modifier = Modifier.height(34.dp)
                            ) {
                                Text(
                                    text = item.productName ?: "Item",
                                    fontSize = 11.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

                    IconButton(onClick = onClearAllPending) {
                        Icon(Icons.Default.Delete, contentDescription = "Clear queue", tint = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}

@Composable
private fun TacticalItemBadge(
    item: PantryItem,
    isHighlighted: Boolean,
    onSelectItem: (PantryItem) -> Unit,
    onConsume: (PantryItem) -> Unit,
    onRestock: (PantryItem) -> Unit,
    onUpdateFillLevel: (PantryItem, FillLevel) -> Unit
) {
    Surface(
        onClick = { onSelectItem(item) },
        shape = RoundedCornerShape(6.dp),
        color = if (isHighlighted)
            MaterialTheme.colorScheme.primary
        else
            MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 6.dp, vertical = 4.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = item.name,
                style = MaterialTheme.typography.labelSmall,
                fontSize = 10.sp,
                fontWeight = if (isHighlighted) FontWeight.Bold else FontWeight.Medium,
                color = if (isHighlighted) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )

            if (item.trackingType == TrackingType.BULK_LEVEL) {
                Text(
                    text = item.activeFill.label,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isHighlighted) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable { onUpdateFillLevel(item, item.activeFill.next()) }
                )
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "${item.activeCount}",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isHighlighted) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}
