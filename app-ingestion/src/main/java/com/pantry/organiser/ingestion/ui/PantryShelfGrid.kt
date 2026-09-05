package com.pantry.organiser.ingestion.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pantry.organiser.core.model.PantryConstants
import com.pantry.organiser.core.model.PantryItem

@Composable
fun PantryShelfGrid(
    selectedCell: Pair<Int, Int>?,
    onCellClick: (Int, Int) -> Unit,
    pantryItems: List<PantryItem> = emptyList(),
    highlightedItem: PantryItem? = null,
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
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                repeat(PantryConstants.ZONES_PER_SHELF) { col ->
                    val shelfNumber = PantryConstants.rowToShelf(row)
                    val zoneIndex = PantryConstants.colToZone(col)
                    val label = "S$shelfNumber-${PantryConstants.getZoneLabel(zoneIndex)}"

                    val isSelected = selectedCell?.first == row && selectedCell?.second == col
                    val isHighlighted = highlightedItem != null &&
                            highlightedItem.shelfNumber == shelfNumber &&
                            highlightedItem.zoneIndex == zoneIndex

                    val itemsInCell = pantryItems.filter { it.shelfNumber == shelfNumber && it.zoneIndex == zoneIndex }

                    ShelfCell(
                        label = label,
                        isSelected = isSelected || isHighlighted,
                        itemCount = itemsInCell.size,
                        onClick = { onCellClick(row, col) },
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
    itemCount: Int = 0,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val hasItems = itemCount > 0

    val backgroundColor by animateColorAsState(
        targetValue = when {
            isSelected -> MaterialTheme.colorScheme.primaryContainer
            else -> MaterialTheme.colorScheme.surface
        },
        animationSpec = tween(durationMillis = 300),
        label = "CellBg"
    )

    val borderColor by animateColorAsState(
        targetValue = when {
            isSelected -> MaterialTheme.colorScheme.primary
            hasItems -> MaterialTheme.colorScheme.outline
            else -> MaterialTheme.colorScheme.outlineVariant
        },
        animationSpec = tween(durationMillis = 300),
        label = "CellBorder"
    )

    Surface(
        modifier = modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick),
        color = backgroundColor,
        border = BorderStroke(
            width = if (isSelected) 2.dp else 1.dp,
            color = borderColor
        ),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxSize()
                .padding(2.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                else MaterialTheme.colorScheme.onSurface,
                fontSize = 12.sp,
                maxLines = 1
            )

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = if (hasItems) (if (itemCount == 1) "1 item" else "$itemCount items") else "Empty",
                style = MaterialTheme.typography.labelSmall,
                fontSize = 10.sp,
                fontWeight = if (hasItems) FontWeight.SemiBold else FontWeight.Normal,
                color = when {
                    isSelected -> MaterialTheme.colorScheme.primary
                    hasItems -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                },
                maxLines = 1
            )
        }
    }
}
