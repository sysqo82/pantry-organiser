package com.pantry.organiser.dashboard.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.pantry.organiser.core.model.FillLevel
import com.pantry.organiser.core.model.PantryConstants
import com.pantry.organiser.core.model.PantryItem
import com.pantry.organiser.core.model.TrackingType
import com.pantry.organiser.dashboard.ui.PantryShelfGrid

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditItemBottomSheet(
    item: PantryItem,
    onDismiss: () -> Unit,
    onSave: (PantryItem) -> Unit,
    onDelete: (PantryItem) -> Unit
) {
    val configuration = LocalConfiguration.current
    val isTablet = configuration.screenWidthDp >= 600

    var name by remember(item.id) { mutableStateOf(item.name) }
    var brand by remember(item.id) { mutableStateOf(item.brand ?: "") }
    var packageQuantity by remember(item.id) { mutableStateOf(item.packageQuantity ?: "") }
    var unitsPerPack by remember(item.id) { mutableStateOf(item.unitsPerPack.toString()) }
    var trackingType by remember(item.id) { mutableStateOf(item.trackingType) }
    var sealedCount by remember(item.id) { mutableStateOf(item.sealedCount.toString()) }
    var activeFill by remember(item.id) { mutableStateOf(item.activeFill) }

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

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Edit Item Details & Location",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = brand,
                    onValueChange = { brand = it },
                    label = { Text("Brand") },
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = packageQuantity,
                    onValueChange = { packageQuantity = it },
                    label = { Text("Weight / Quantity per pack") },
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = unitsPerPack,
                    onValueChange = { if (it.all { char -> char.isDigit() }) unitsPerPack = it },
                    label = { Text("Units per pack / Bundle size") },
                    placeholder = { Text("e.g. 4 for a 4-pack") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text("Tracking Mode", style = MaterialTheme.typography.titleMedium)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = trackingType == TrackingType.DISCRETE_COUNT,
                        onClick = { trackingType = TrackingType.DISCRETE_COUNT },
                        label = { Text("Discrete Units") },
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = trackingType == TrackingType.BULK_LEVEL,
                        onClick = { trackingType = TrackingType.BULK_LEVEL },
                        label = { Text("Bulk / Staple Level") },
                        modifier = Modifier.weight(1f)
                    )
                }

                if (trackingType == TrackingType.DISCRETE_COUNT) {
                    OutlinedTextField(
                        value = sealedCount,
                        onValueChange = { if (it.all { char -> char.isDigit() }) sealedCount = it },
                        label = { Text("Sealed Packs / Units Count") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    Text("Active Pack Level", style = MaterialTheme.typography.titleMedium)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FillLevel.entries.forEach { level ->
                            FilterChip(
                                selected = activeFill == level,
                                onClick = { activeFill = level },
                                label = { Text(level.label) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Location: Shelf ${4 - selectedRow}, Zone ${PantryConstants.getZoneLabel(selectedCol + 1)}",
                    style = MaterialTheme.typography.titleMedium
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

                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    OutlinedButton(
                        onClick = { showDeleteConfirm = true },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Delete")
                    }

                    Button(
                        onClick = {
                            val parsedUnits = unitsPerPack.toIntOrNull() ?: 1
                            val parsedSealed = sealedCount.toIntOrNull() ?: 0
                            val targetShelf = 4 - selectedRow
                            val targetZone = selectedCol + 1

                            val updatedItem = item.copy(
                                name = name.ifBlank { "Unnamed Item" },
                                brand = brand.ifBlank { null },
                                packageQuantity = packageQuantity.ifBlank { null },
                                unitsPerPack = parsedUnits,
                                trackingType = trackingType,
                                sealedCount = parsedSealed,
                                activeFill = activeFill,
                                shelfNumber = targetShelf,
                                zoneIndex = targetZone,
                                updatedAt = System.currentTimeMillis()
                            )
                            onSave(updatedItem)
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Save, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Save")
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete Item?") },
            text = { Text("Are you sure you want to remove '${item.name}' from your inventory?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirm = false
                        onDelete(item)
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
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
