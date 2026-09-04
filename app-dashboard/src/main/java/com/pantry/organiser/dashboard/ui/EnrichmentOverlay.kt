package com.pantry.organiser.dashboard.ui

import androidx.compose.animation.*
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.pantry.organiser.core.model.FillLevel
import com.pantry.organiser.core.model.PantryItem
import com.pantry.organiser.core.model.TrackingType
import com.pantry.organiser.dashboard.data.SyncQueueItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EnrichmentOverlay(
    syncItem: SyncQueueItem,
    existingItem: PantryItem?,
    onSave: (Int, Int, Int, FillLevel) -> Unit,
    onDismiss: () -> Unit
) {
    var quantityToAdd by remember { mutableIntStateOf(1) }
    var selectedFillLevel by remember { mutableStateOf(existingItem?.activeFill ?: FillLevel.FULL) }
    var selectedRow by remember { mutableIntStateOf(existingItem?.shelfNumber?.let { 4 - it } ?: 0) }
    var selectedCol by remember { mutableIntStateOf(existingItem?.zoneIndex?.let { it - 1 } ?: 1) }

    val isExisting = existingItem != null

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.8f))
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier
                .widthIn(max = 600.dp)
                .wrapContentHeight(),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isExisting) "Restock Item" else "New Item Discovery",
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
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (syncItem.imageUrl != null) {
                            AsyncImage(
                                model = syncItem.imageUrl,
                                contentDescription = null,
                                modifier = Modifier
                                    .size(80.dp)
                                    .clip(RoundedCornerShape(8.dp)),
                                contentScale = ContentScale.Fit
                            )
                        }
                        Spacer(Modifier.width(16.dp))
                        Column {
                            Text(
                                syncItem.productName ?: "Unknown Product",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            if (syncItem.brand != null) {
                                Text(syncItem.brand, style = MaterialTheme.typography.bodySmall)
                            }
                            if (syncItem.quantity != null) {
                                Text(syncItem.quantity, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }

                Text("How many did you buy?", style = MaterialTheme.typography.titleMedium)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    IconButton(
                        onClick = { if (quantityToAdd > 1) quantityToAdd-- },
                        modifier = Modifier.size(56.dp).background(MaterialTheme.colorScheme.secondaryContainer, CircleShape)
                    ) {
                        Icon(Icons.Default.Remove, contentDescription = "Decrease")
                    }
                    Text(
                        text = quantityToAdd.toString(),
                        style = MaterialTheme.typography.displayMedium,
                        fontWeight = FontWeight.Black,
                        modifier = Modifier.padding(horizontal = 32.dp)
                    )
                    IconButton(
                        onClick = { quantityToAdd++ },
                        modifier = Modifier.size(56.dp).background(MaterialTheme.colorScheme.secondaryContainer, CircleShape)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Increase")
                    }
                }

                Text(
                    text = if (isExisting && existingItem != null) "Stored at S${existingItem.shelfNumber}-${existingItem.zoneIndex}" else "Assign a shelf",
                    style = MaterialTheme.typography.titleMedium,
                    color = if (isExisting) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(240.dp)
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
                    modifier = Modifier.fillMaxWidth().height(64.dp),
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
