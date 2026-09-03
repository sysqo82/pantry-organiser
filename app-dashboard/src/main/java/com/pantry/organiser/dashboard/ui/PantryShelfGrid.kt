package com.pantry.organiser.dashboard.ui

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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pantry.organiser.core.model.PantryConstants
import com.pantry.organiser.core.model.PantryItem
import com.pantry.organiser.dashboard.ui.components.ProductThumbnail

@Composable
fun PantryShelfGrid(
    selectedCell: Pair<Int, Int>?,
    onCellClick: (Int, Int) -> Unit,
    pantryItems: List<PantryItem> = emptyList(),
    highlightedItem: PantryItem? = null,
    onSelectItem: ((PantryItem) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        repeat(PantryConstants.TOTAL_SHELVES) { row ->
            Row(
                modifier = Modifier.fillMaxWidth().weight(1f),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                repeat(PantryConstants.ZONES_PER_SHELF) { col ->
                    val shelfNumber = PantryConstants.rowToShelf(row)
                    val zoneIndex = PantryConstants.colToZone(col)
                    val label = "S$shelfNumber-${PantryConstants.getZoneLabel(zoneIndex)}"

                    val isSelected = selectedCell?.first == row && selectedCell?.second == col
                    val isHighlightedLocation = highlightedItem != null &&
                            highlightedItem.shelfNumber == shelfNumber &&
                            highlightedItem.zoneIndex == zoneIndex

                    val itemsInCell = pantryItems.filter { it.shelfNumber == shelfNumber && it.zoneIndex == zoneIndex }
                    val displayItem = highlightedItem.takeIf { isHighlightedLocation } ?: itemsInCell.firstOrNull()

                    ShelfCell(
                        label = label,
                        isSelected = isSelected || isHighlightedLocation,
                        item = displayItem,
                        itemCount = itemsInCell.size,
                        onClick = {
                            onCellClick(row, col)
                            if (displayItem != null && onSelectItem != null) {
                                onSelectItem(displayItem)
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("ShelfCell_$label")
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
    item: PantryItem? = null,
    itemCount: Int = 0,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isShowingItem = item != null

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
            .fillMaxHeight()
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
        if (isShowingItem && item != null) {
            ProductThumbnail(
                imageUrl = item.imageUrl,
                apiImageUrl = item.apiImageUrl,
                localImageUri = item.localImageUri,
                itemName = item.name,
                thumbnailSize = null,
                updatedAt = item.updatedAt,
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
                text = if (itemCount > 1) "$label ($itemCount)" else label,
                color = if (isShowingItem) Color.White
                else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                fontSize = if (isShowingItem) 10.sp else 12.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
            )
        }
    }
}
