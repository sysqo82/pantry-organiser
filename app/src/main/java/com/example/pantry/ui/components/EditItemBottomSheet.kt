package com.pantry.organiser.ui.components

import androidx.compose.foundation.horizontalScroll
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
import com.pantry.organiser.data.FillLevel
import com.pantry.organiser.data.PantryItem
import com.pantry.organiser.data.TrackingType

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

    var name by remember { mutableStateOf(item.name) }
    var brand by remember { mutableStateOf(item.brand ?: "") }
    var packageQuantity by remember { mutableStateOf(item.packageQuantity ?: "") }
    var trackingType by remember { mutableStateOf(item.trackingType) }
    var sealedCount by remember { mutableStateOf(item.sealedCount.toString()) }
    var activeFill by remember { mutableStateOf(item.activeFill) }
    
    // Map 1..4 shelf number to 0..3 UI row with clamping
    var selectedRow by remember { mutableIntStateOf((4 - item.shelfNumber.coerceIn(1, 4)).coerceIn(0, 3)) }
    var selectedCol by remember { mutableIntStateOf((item.zoneIndex.coerceIn(1, 3) - 1).coerceIn(0, 2)) }
    
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
                Text(
                    text = "Edit Item",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                
                Spacer(modifier = Modifier.height(16.dp))

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

                Spacer(modifier = Modifier.height(24.dp))

                Text("Tracking Mode", style = MaterialTheme.typography.titleMedium)
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = trackingType == TrackingType.DISCRETE_COUNT,
                        onClick = { trackingType = TrackingType.DISCRETE_COUNT },
                        label = { Text("Discrete Count") },
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = trackingType == TrackingType.BULK_LEVEL,
                        onClick = { trackingType = TrackingType.BULK_LEVEL },
                        label = { Text("Bulk / Staple") },
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
                            onClick = { activeFill = level },
                            label = { Text(level.name.replace("_", " ").lowercase().replaceFirstChar { it.uppercase() }) }
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
                        onCellClick = { r, c -> selectedRow = r; selectedCol = c },
                        highlightedItem = item.copy(
                            shelfNumber = (4 - selectedRow).coerceIn(1, 4),
                            zoneIndex = (selectedCol + 1).coerceIn(1, 3)
                        ),
                        modifier = Modifier.fillMaxSize()
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = {
                        val mappedShelf = (4 - selectedRow).coerceIn(1, 4)
                        val mappedZone = (selectedCol + 1).coerceIn(1, 3)
                        
                        onSave(item.copy(
                            name = name,
                            brand = brand,
                            packageQuantity = packageQuantity,
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

                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete Item?") },
            text = { Text("Are you sure you want to remove '$name' from your pantry?") },
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
