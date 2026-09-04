package com.pantry.organiser.ui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Fastfood
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.pantry.organiser.core.model.FillLevel
import com.pantry.organiser.core.model.PantryItem
import com.pantry.organiser.core.model.TrackingType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditItemBottomSheet(
    item: PantryItem,
    isReadOnly: Boolean = false, // New parameter for Role Enforcement
    onDismiss: () -> Unit,
    onSave: (PantryItem) -> Unit,
    onDelete: (PantryItem) -> Unit,
    onRestoreApiImage: () -> Unit = {} // New feature callback
) {
    val configuration = LocalConfiguration.current
    val isTablet = configuration.screenWidthDp >= 600

    var name by remember(item.id) { mutableStateOf(item.name) }
    var brand by remember(item.id) { mutableStateOf(item.brand ?: "") }
    var packageQuantity by remember(item.id) { mutableStateOf(item.packageQuantity ?: "") }
    var unitsPerPack by remember(item.id) { mutableStateOf(item.unitsPerPack.toString()) }
    var activeCount by remember(item.id) { mutableStateOf(item.activeCount.toString()) }
    var trackingType by remember(item.id) { mutableStateOf(item.trackingType) }
    var sealedCount by remember(item.id) { mutableStateOf(item.sealedCount.toString()) }
    var activeFill by remember(item.id) { mutableStateOf(item.activeFill) }
    
    // Map 1..4 shelf number to 0..3 UI row with clamping
    var selectedRow by remember(item.id) { mutableIntStateOf((4 - item.shelfNumber.coerceIn(1, 4)).coerceIn(0, 3)) }
    var selectedCol by remember(item.id) { mutableIntStateOf((item.zoneIndex.coerceIn(1, 3) - 1).coerceIn(0, 2)) }
    
    var showDeleteConfirm by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = { 
            BottomSheetDefaults.DragHandle(
                modifier = Modifier.padding(top = 8.dp, bottom = 0.dp)
            ) 
        },
        contentWindowInsets = { WindowInsets(0) }
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = if (isTablet) 600.dp else 2000.dp)
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                ProductThumbnail(
                    imageUrl = item.imageUrl,
                    apiImageUrl = item.apiImageUrl,
                    localImageUri = item.localImageUri,
                    itemName = name,
                    updatedAt = item.updatedAt,
                    thumbnailSize = 120.dp
                )
                
                // Show Restore if we have a custom photo (local or remote) AND an API image to restore to
                val currentImageUrl = item.imageUrl
                val hasCustomPhoto = item.localImageUri != null || (currentImageUrl != null && !currentImageUrl.contains("openfoodfacts.org"))
                val hasOriginalImage = item.apiImageUrl != null
                
                if (!isReadOnly && hasCustomPhoto && hasOriginalImage) {
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = onRestoreApiImage,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    ) {
                        Icon(Icons.Default.Fastfood, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Restore Original API Image")
                    }
                    Text(
                        "This will delete your custom photo.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = if (isReadOnly) "Item Details" else "Edit Item",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    enabled = !isReadOnly,
                    modifier = Modifier.fillMaxWidth()
                )
                
                OutlinedTextField(
                    value = brand,
                    onValueChange = { brand = it },
                    label = { Text("Brand") },
                    enabled = !isReadOnly,
                    modifier = Modifier.fillMaxWidth()
                )
                
                OutlinedTextField(
                    value = packageQuantity,
                    onValueChange = { packageQuantity = it },
                    label = { Text("Weight / Quantity per pack") },
                    enabled = !isReadOnly,
                    modifier = Modifier.fillMaxWidth()
                )

                val itemBarcode = item.barcode
                if (!itemBarcode.isNullOrBlank()) {
                    OutlinedTextField(
                        value = itemBarcode,
                        onValueChange = {},
                        readOnly = true,
                        enabled = false,
                        label = { Text("Barcode") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                OutlinedTextField(
                    value = unitsPerPack,
                    onValueChange = { if (it.all { char -> char.isDigit() }) unitsPerPack = it },
                    label = { Text("Units per pack / Bundle size") },
                    placeholder = { Text("e.g. 4 for a 4-pack") },
                    enabled = !isReadOnly,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )

                if ((unitsPerPack.toIntOrNull() ?: 1) > 1) {
                    OutlinedTextField(
                        value = activeCount,
                        onValueChange = { if (it.all { char -> char.isDigit() }) activeCount = it },
                        label = { Text("Units left in active pack") },
                        enabled = !isReadOnly,
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                Text("Tracking Mode", style = MaterialTheme.typography.titleMedium)
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = trackingType == TrackingType.DISCRETE_COUNT,
                        onClick = { if (!isReadOnly) trackingType = TrackingType.DISCRETE_COUNT },
                        label = { Text("Discrete Count") },
                        enabled = !isReadOnly,
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = trackingType == TrackingType.BULK_LEVEL,
                        onClick = { if (!isReadOnly) trackingType = TrackingType.BULK_LEVEL },
                        label = { Text("Bulk / Staple") },
                        enabled = !isReadOnly,
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = sealedCount,
                        onValueChange = { if (it.all { char -> char.isDigit() }) sealedCount = it },
                        label = { Text("Sealed Count") },
                        enabled = !isReadOnly,
                        modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
                
                Text("Active Fill Level", style = MaterialTheme.typography.titleMedium)
                ScrollableRow(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    FillLevel.entries.forEach { level ->
                        FilterChip(
                            selected = activeFill == level,
                            onClick = { if (!isReadOnly) activeFill = level },
                            label = { Text(level.name.replace("_", " ").lowercase().replaceFirstChar { it.uppercase() }) },
                            enabled = !isReadOnly
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                
                Text("Shelf Location", style = MaterialTheme.typography.titleMedium)
                
                // Fixed height container to prevent grid collapse in scrollable column
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(280.dp) // Strict height to guarantee uniform row distribution
                        .padding(vertical = 8.dp)
                ) {
                    PantryShelfGrid(
                        selectedCell = selectedRow to selectedCol,
                        onCellClick = { r, c -> if (!isReadOnly) { selectedRow = r; selectedCol = c } },
                        highlightedItem = item.copy(
                            shelfNumber = (4 - selectedRow).coerceIn(1, 4),
                            zoneIndex = (selectedCol + 1).coerceIn(1, 3)
                        ),
                        modifier = Modifier.fillMaxSize()
                    )
                }

                if (!isReadOnly) {
                    Spacer(modifier = Modifier.height(24.dp))

                    Button(
                        onClick = {
                            val mappedShelf = (4 - selectedRow).coerceIn(1, 4)
                            val mappedZone = (selectedCol + 1).coerceIn(1, 3)
                            
                            onSave(item.copy(
                                name = name,
                                brand = brand,
                                packageQuantity = packageQuantity,
                                unitsPerPack = unitsPerPack.toIntOrNull() ?: 1,
                                activeCount = activeCount.toIntOrNull() ?: 1,
                                trackingType = trackingType,
                                sealedCount = sealedCount.toIntOrNull() ?: 0,
                                activeFill = activeFill,
                                shelfNumber = mappedShelf,
                                zoneIndex = mappedZone
                            ))
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Icon(Icons.Default.Save, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Save Changes")
                    }

                    TextButton(
                        onClick = { showDeleteConfirm = true },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Delete Item")
                    }
                } else {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "Editing is disabled on mobile. Use your tablet to manage inventory.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 24.dp)
                    )
                }

                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete Item?") },
            text = { Text("Are you sure you want to remove '$name' from your organiser?") },
            confirmButton = {
                Button(
                    onClick = { onDelete(item) },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun ScrollableRow(
    modifier: Modifier = Modifier,
    horizontalArrangement: Arrangement.Horizontal = Arrangement.Start,
    content: @Composable RowScope.() -> Unit
) {
    Row(
        modifier = modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = horizontalArrangement,
        verticalAlignment = Alignment.CenterVertically,
        content = content
    )
}
