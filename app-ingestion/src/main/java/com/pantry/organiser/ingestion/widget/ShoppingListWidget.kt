package com.pantry.organiser.ingestion.widget

import android.content.Context
import android.content.Intent
import android.text.format.DateUtils
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.state.GlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.pantry.organiser.ingestion.MainActivity
import com.pantry.organiser.ingestion.R

class ShoppingListWidget : GlanceAppWidget() {

    override val stateDefinition: GlanceStateDefinition<*> = ShoppingWidgetStateDefinition
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            GlanceTheme {
                val widgetState = currentState<ShoppingWidgetState>()
                ShoppingListWidgetContent(context = context, state = widgetState)
            }
        }
    }

    @Composable
    private fun ShoppingListWidgetContent(
        context: Context,
        state: ShoppingWidgetState
    ) {
        val backgroundColor = GlanceTheme.colors.widgetBackground
        val surfaceColor = GlanceTheme.colors.surface
        val primaryTextColor = GlanceTheme.colors.onSurface
        val secondaryTextColor = GlanceTheme.colors.onSurfaceVariant

        val openScannerIntent = Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            putExtra(MainActivity.EXTRA_OPEN_MODE, "INSERT")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val itemCountText: String = when (state) {
            is ShoppingWidgetState.Success -> {
                val status = state.statusMessage
                if (!status.isNullOrBlank()) {
                    status
                } else {
                    val sizeCount = state.items.size
                    val countPart = "$sizeCount ${if (sizeCount == 1) "item" else "items"}"
                    val relativeTime = DateUtils.getRelativeTimeSpanString(
                        state.lastUpdatedMillis,
                        System.currentTimeMillis(),
                        DateUtils.MINUTE_IN_MILLIS
                    )
                    "$countPart • $relativeTime"
                }
            }
            is ShoppingWidgetState.Loading -> "Syncing list..."
            is ShoppingWidgetState.Error -> "Sync error"
        }

        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(backgroundColor)
                .padding(12.dp)
        ) {
            // Header
            Row(
                modifier = GlanceModifier.fillMaxWidth().padding(bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = GlanceModifier.defaultWeight()) {
                    Text(
                        text = context.getString(R.string.widget_shopping_list_title),
                        style = TextStyle(
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = primaryTextColor
                        )
                    )
                    Text(
                        text = itemCountText,
                        style = TextStyle(
                            fontSize = 11.sp,
                            color = secondaryTextColor
                        ),
                        maxLines = 1
                    )
                }

                // Refresh Button
                Box(
                    modifier = GlanceModifier
                        .clickable(actionRunCallback<RefreshShoppingListAction>())
                        .padding(4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "↻",
                        style = TextStyle(
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = primaryTextColor
                        )
                    )
                }
            }

            Spacer(modifier = GlanceModifier.height(4.dp))

            // Body Content
            when (state) {
                is ShoppingWidgetState.Loading -> {
                    Box(
                        modifier = GlanceModifier
                            .fillMaxSize()
                            .background(surfaceColor)
                            .padding(12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Loading list...",
                            style = TextStyle(
                                fontSize = 12.sp,
                                color = secondaryTextColor
                            )
                        )
                    }
                }
                is ShoppingWidgetState.Error -> {
                    Box(
                        modifier = GlanceModifier
                            .fillMaxSize()
                            .background(surfaceColor)
                            .padding(12.dp)
                            .clickable(actionRunCallback<RefreshShoppingListAction>()),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "Failed to load",
                                style = TextStyle(
                                    fontSize = 12.sp,
                                    color = secondaryTextColor
                                )
                            )
                            Spacer(modifier = GlanceModifier.height(4.dp))
                            Text(
                                text = "Tap to retry ↻",
                                style = TextStyle(
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = primaryTextColor
                                )
                            )
                        }
                    }
                }
                is ShoppingWidgetState.Success -> {
                    val items = state.items
                    if (items.isEmpty()) {
                        Box(
                            modifier = GlanceModifier
                                .fillMaxSize()
                                .background(surfaceColor)
                                .padding(12.dp)
                                .clickable(actionStartActivity(openScannerIntent)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = context.getString(R.string.widget_shopping_list_empty),
                                style = TextStyle(
                                    fontSize = 12.sp,
                                    color = secondaryTextColor
                                )
                            )
                        }
                    } else {
                        // Fill all available height with LazyColumn
                        LazyColumn(
                            modifier = GlanceModifier.fillMaxSize()
                        ) {
                            items(items) { item ->
                                ShoppingListItemRow(
                                    item = item,
                                    isExpanded = state.expandedItemId == item.id,
                                    surfaceColor = surfaceColor,
                                    primaryTextColor = primaryTextColor,
                                    secondaryTextColor = secondaryTextColor
                                )
                                Spacer(modifier = GlanceModifier.height(6.dp))
                            }
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun ShoppingListItemRow(
        item: WidgetShoppingItem,
        isExpanded: Boolean,
        surfaceColor: ColorProvider,
        primaryTextColor: ColorProvider,
        secondaryTextColor: ColorProvider
    ) {
        val toggleParams = actionParametersOf(
            ShoppingWidgetActionKeys.ITEM_ID to item.id
        )

        val boughtParams = actionParametersOf(
            ShoppingWidgetActionKeys.ITEM_ID to item.id,
            ShoppingWidgetActionKeys.ITEM_NAME to item.name,
            ShoppingWidgetActionKeys.ITEM_BRAND to (item.brand ?: ""),
            ShoppingWidgetActionKeys.ITEM_QTY to (item.packageQuantity ?: ""),
            ShoppingWidgetActionKeys.ITEM_BARCODE to (item.barcode ?: "")
        )

        val dismissParams = actionParametersOf(
            ShoppingWidgetActionKeys.ITEM_ID to item.id,
            ShoppingWidgetActionKeys.ITEM_NAME to item.name
        )

        Column(
            modifier = GlanceModifier
                .fillMaxWidth()
                .background(surfaceColor)
                .padding(8.dp)
        ) {
            // Main Item Row
            Row(
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .clickable(actionRunCallback<ToggleItemExpandAction>(toggleParams)),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isExpanded) "▼" else "•",
                    style = TextStyle(
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = primaryTextColor
                    ),
                    modifier = GlanceModifier.padding(end = 8.dp)
                )

                // Product Name as Main Highlight
                Column(modifier = GlanceModifier.defaultWeight()) {
                    Text(
                        text = item.name,
                        style = TextStyle(
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = primaryTextColor
                        ),
                        maxLines = 1
                    )

                    val secondaryDetails = listOfNotNull(item.brand, item.packageQuantity)
                        .filter { it.isNotBlank() }
                        .joinToString(" • ")

                    if (secondaryDetails.isNotBlank()) {
                        Text(
                            text = secondaryDetails,
                            style = TextStyle(
                                fontSize = 11.sp,
                                color = secondaryTextColor
                            ),
                            maxLines = 1
                        )
                    }
                }
            }

            // Expanded Action Bar
            if (isExpanded) {
                Spacer(modifier = GlanceModifier.height(8.dp))
                Row(
                    modifier = GlanceModifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Bought Button
                    Box(
                        modifier = GlanceModifier
                            .defaultWeight()
                            .background(GlanceTheme.colors.primaryContainer)
                            .padding(vertical = 6.dp, horizontal = 8.dp)
                            .clickable(actionRunCallback<MarkItemBoughtAction>(boughtParams)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "✓ Bought",
                            style = TextStyle(
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = GlanceTheme.colors.onPrimaryContainer
                            )
                        )
                    }

                    Spacer(modifier = GlanceModifier.width(8.dp))

                    // Remove Button
                    Box(
                        modifier = GlanceModifier
                            .defaultWeight()
                            .background(GlanceTheme.colors.errorContainer)
                            .padding(vertical = 6.dp, horizontal = 8.dp)
                            .clickable(actionRunCallback<DismissItemAction>(dismissParams)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "✕ Remove",
                            style = TextStyle(
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = GlanceTheme.colors.onErrorContainer
                            )
                        )
                    }
                }
            }
        }
    }
}
