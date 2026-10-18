package com.pantry.organiser.ingestion.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pantry.organiser.ingestion.CheckedNotFoundItem

@Composable
fun CheckedNotFoundBanner(
    info: CheckedNotFoundItem,
    onDismiss: () -> Unit,
    onViewPhotos: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val hasSimilar = info.similarItems.isNotEmpty()
    val backgroundColor = if (hasSimilar) Color(0xFFE65100) else Color(0xFFD32F2F)
    Surface(
        color = backgroundColor,
        shape = RoundedCornerShape(14.dp),
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .then(
                if (hasSimilar && onViewPhotos != null) {
                    Modifier.clickable { onViewPhotos() }
                } else Modifier
            )
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(
                        text = if (hasSimilar) {
                            if (info.isPastSimilar) "PREVIOUSLY OWNED SIMILAR PRODUCT" else "NOT IN PANTRY (SIMILAR PRODUCT FOUND)"
                        } else "NOT IN PANTRY",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 11.sp,
                        color = Color.White.copy(alpha = 0.9f)
                    )
                    if (hasSimilar) {
                        val detailsText = if (info.similarItems.size == 1) {
                            val item = info.similarItems.first()
                            val loc = if (info.isPastSimilar) "Former Location: ${item.formattedLocation}" else item.formattedLocation
                            "${item.name} ($loc)"
                        } else {
                            val count = info.similarItems.size
                            val shelvesText = info.similarItems.map { it.formattedLocation }.distinct().joinToString(", ")
                            if (info.isPastSimilar) "$count similar items previously owned ($shelvesText)" else "$count similar items in stock ($shelvesText)"
                        }
                        Text(
                            text = if (info.isPastSimilar) "Not in stock, but you previously had $detailsText." else "Not found by barcode, but you have $detailsText.",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "Tap banner to view product photos & details",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White.copy(alpha = 0.9f),
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    } else {
                        Text(
                            text = "${info.productName}${if (!info.brand.isNullOrBlank()) " (${info.brand})" else ""} is not in stock.",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            }
            IconButton(onClick = onDismiss) {
                Icon(
                    imageVector = Icons.Default.Clear,
                    contentDescription = "Dismiss",
                    tint = Color.White
                )
            }
        }
    }
}
