package com.pantry.organiser.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Inventory
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pantry.organiser.data.FillLevel
import com.pantry.organiser.data.PantryItem
import com.pantry.organiser.data.TrackingType

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PantryItemCard(
    item: PantryItem,
    isHighlighted: Boolean,
    isReadOnly: Boolean, // New parameter for Device-Role enforcement
    onConsume: () -> Unit,
    onAddSealed: () -> Unit,
    onRemoveSealed: () -> Unit,
    onIncrementFill: () -> Unit,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onEditClick: () -> Unit,
    onPhotoRequest: () -> Unit,
    modifier: Modifier = Modifier
) {
    val backgroundColor by animateColorAsState(
        targetValue = if (isHighlighted) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        animationSpec = tween(durationMillis = 600),
        label = "PulseAnimation"
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        colors = CardDefaults.cardColors(
            containerColor = backgroundColor
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(8.dp)
                .heightIn(min = 88.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ProductThumbnail(
                imageUrl = item.imageUrl,
                apiImageUrl = item.apiImageUrl, // High priority source
                localImageUri = item.localImageUri,
                itemName = item.name,
                updatedAt = item.updatedAt,
                modifier = Modifier
                    .padding(4.dp)
                    .clickable { onPhotoRequest() }
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = item.name, 
                    style = MaterialTheme.typography.titleMedium, 
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${item.brand ?: ""} • ${item.packageQuantity ?: ""}", 
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                Spacer(modifier = Modifier.height(4.dp))
                
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = MaterialTheme.shapes.small
                    ) {
                        Text(
                            text = getCellLabel(4 - item.safeShelfNumber, item.safeZoneIndex - 1),
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }

            if (!isReadOnly) {
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Sealed Pill
                    SealedStockPill(
                        count = if (item.trackingType == TrackingType.DISCRETE_COUNT) item.sealedCount + 1 else item.sealedCount,
                        label = if (item.trackingType == TrackingType.DISCRETE_COUNT) "Count" else "Sealed",
                        onAdd = onAddSealed,
                        onRemove = onRemoveSealed
                    )

                    // Active Fill Control (Only for Staples)
                    if (item.trackingType == TrackingType.BULK_LEVEL) {
                        ActiveFillControl(
                            fillLevel = item.activeFill,
                            onConsume = onConsume,
                            onRefill = onIncrementFill
                        )
                    } else {
                        // For discrete items, a simple Consume button
                        Button(
                            onClick = onConsume,
                            modifier = Modifier.height(32.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                            shape = CircleShape,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                                contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                        ) {
                            Icon(Icons.Default.Remove, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Consume", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            } else {
                // Read-Only Summary View
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.Center
                ) {
                    val count = if (item.trackingType == TrackingType.DISCRETE_COUNT) item.sealedCount + 1 else item.sealedCount
                    val label = if (item.trackingType == TrackingType.DISCRETE_COUNT) "Count" else "Sealed"
                    
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        shape = CircleShape
                    ) {
                        Text(
                            text = "$count $label",
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    
                    if (item.trackingType == TrackingType.BULK_LEVEL) {
                        Spacer(Modifier.height(4.dp))
                        FillLevelBadge(item.activeFill)
                    }
                }
            }

            IconButton(onClick = onEditClick) {
                Icon(Icons.Default.MoreVert, contentDescription = "Edit Item")
            }
        }
    }
}

@Composable
fun SealedStockPill(
    count: Int,
    label: String = "Sealed",
    onAdd: () -> Unit,
    onRemove: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        shape = CircleShape,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Inventory,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "$count $label",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(modifier = Modifier.width(4.dp))
            
            IconButton(
                onClick = onRemove,
                modifier = Modifier.size(24.dp)
            ) {
                Icon(Icons.Default.Remove, contentDescription = "Remove Sealed", modifier = Modifier.size(16.dp))
            }
            
            IconButton(
                onClick = onAdd,
                modifier = Modifier.size(24.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Sealed", modifier = Modifier.size(16.dp))
            }
        }
    }
}

@Composable
fun ActiveFillControl(
    fillLevel: FillLevel,
    onConsume: () -> Unit,
    onRefill: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        IconButton(
            onClick = onConsume,
            modifier = Modifier.size(32.dp)
        ) {
            Icon(Icons.Default.Remove, contentDescription = "Consume portion")
        }

        FillLevelBadge(fillLevel)

        IconButton(
            onClick = onRefill,
            modifier = Modifier.size(32.dp)
        ) {
            Icon(Icons.Default.Add, contentDescription = "Refill portion")
        }
    }
}

@Composable
fun FillLevelBadge(level: FillLevel) {
    val color = when (level) {
        FillLevel.FULL, FillLevel.THREE_QUARTERS -> Color(0xFF4CAF50) // Green
        FillLevel.HALF -> Color(0xFFFFC107) // Amber
        FillLevel.LOW -> Color(0xFFFF9800) // Orange
        FillLevel.EMPTY -> Color(0xFFF44336) // Red
    }

    Surface(
        color = color.copy(alpha = 0.2f),
        contentColor = color,
        shape = MaterialTheme.shapes.small,
        border = BorderStroke(1.dp, color)
    ) {
        Text(
            text = level.name.replace("_", " ").lowercase().replaceFirstChar { it.uppercase() },
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold
        )
    }
}
