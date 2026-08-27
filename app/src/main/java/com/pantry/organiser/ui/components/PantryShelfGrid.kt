package com.pantry.organiser.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pantry.organiser.data.PantryItem

@Composable
fun PantryShelfGrid(
    selectedCell: Pair<Int, Int>?,
    onCellClick: (Int, Int) -> Unit,
    highlightedItem: PantryItem? = null,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(if (modifier == Modifier) 16.dp else 0.dp) // Only apply outer padding if not passed a specific modifier
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        repeat(4) { row ->
            Row(
                modifier = Modifier.fillMaxWidth().weight(1f),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                repeat(3) { col ->
                    val isSelected = selectedCell?.first == row && selectedCell?.second == col
                    val isItemLocation = highlightedItem != null && 
                                       highlightedItem.safeShelfNumber == (4 - row) && 
                                       highlightedItem.safeZoneIndex == (col + 1)
                    
                    ShelfCell(
                        label = getCellLabel(row, col),
                        isSelected = isSelected,
                        imageUrl = if (isItemLocation) highlightedItem?.imageUrl else null,
                        apiImageUrl = if (isItemLocation) highlightedItem?.apiImageUrl else null,
                        localImageUri = if (isItemLocation) highlightedItem?.localImageUri else null,
                        itemInitial = if (isItemLocation) highlightedItem?.name?.take(1)?.uppercase() else null,
                        updatedAt = if (isItemLocation) highlightedItem?.updatedAt ?: 0L else 0L,
                        onClick = { onCellClick(row, col) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
fun ShelfCell(
    label: String,
    isSelected: Boolean,
    imageUrl: String? = null,
    apiImageUrl: String? = null,
    localImageUri: String? = null,
    itemInitial: String? = null,
    updatedAt: Long = 0L,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isShowingItem = imageUrl != null || apiImageUrl != null || localImageUri != null || itemInitial != null

    val backgroundColor by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                      else MaterialTheme.colorScheme.surface,
        animationSpec = tween(durationMillis = 600),
        label = "CellPulse"
    )

    val borderColor by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
        animationSpec = tween(durationMillis = 600),
        label = "BorderPulse"
    )

    Box(
        modifier = modifier
            .fillMaxHeight() // Enforce filling row height
            .clip(RoundedCornerShape(4.dp))
            .background(backgroundColor)
            .border(
                width = 2.dp,
                color = borderColor,
                shape = RoundedCornerShape(4.dp)
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (isShowingItem) {
            ProductThumbnail(
                imageUrl = imageUrl,
                apiImageUrl = apiImageUrl,
                localImageUri = localImageUri,
                itemName = itemInitial ?: "",
                thumbnailSize = null,
                updatedAt = updatedAt,
                modifier = Modifier.fillMaxSize()
            )
        }

        // Label overlay
        Surface(
            color = if (isShowingItem) Color.Black.copy(alpha = 0.6f) else Color.Transparent,
            shape = RoundedCornerShape(topEnd = 4.dp),
            modifier = Modifier.align(Alignment.BottomStart)
        ) {
            Text(
                text = label,
                color = if (isShowingItem) Color.White 
                        else if (isSelected) MaterialTheme.colorScheme.onSurface 
                        else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                fontSize = if (isShowingItem) 8.sp else 12.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
            )
        }
    }
}

fun getCellLabel(row: Int, col: Int): String {
    val shelf = (4 - row).coerceIn(1, 4)
    val zone = when (col.coerceIn(0, 2)) {
        0 -> "L"
        1 -> "M"
        else -> "R"
    }
    return "S$shelf-$zone"
}
