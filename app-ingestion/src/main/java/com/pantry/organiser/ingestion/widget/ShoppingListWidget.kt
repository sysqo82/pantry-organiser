package com.pantry.organiser.ingestion.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
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
import androidx.glance.state.GlanceStateDefinition
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.pantry.organiser.ingestion.MainActivity
import com.pantry.organiser.ingestion.R
import kotlinx.serialization.json.Json

class ShoppingListWidget : GlanceAppWidget() {

    override val stateDefinition: GlanceStateDefinition<*> = PreferencesGlanceStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            GlanceTheme {
                val prefs = currentState<Preferences>()
                val jsonString = prefs[ShoppingListSyncWorker.KEY_SHOPPING_ITEMS] ?: "[]"
                val items = try {
                    Json.decodeFromString<List<WidgetShoppingItem>>(jsonString)
                } catch (_: Exception) {
                    emptyList()
                }

                ShoppingListWidgetContent(context = context, items = items)
            }
        }
    }

    @Composable
    private fun ShoppingListWidgetContent(
        context: Context,
        items: List<WidgetShoppingItem>
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

        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(backgroundColor)
                .padding(12.dp)
        ) {
            // Header
            Row(
                modifier = GlanceModifier.fillMaxWidth().padding(bottom = 8.dp),
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
                        text = "${items.size} ${if (items.size == 1) "item" else "items"}",
                        style = TextStyle(
                            fontSize = 11.sp,
                            color = secondaryTextColor
                        )
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

            // Body
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
                            fontSize = 13.sp,
                            color = secondaryTextColor
                        )
                    )
                }
            } else {
                LazyColumn(
                    modifier = GlanceModifier.fillMaxSize()
                ) {
                    items(items) { item ->
                        ShoppingListItemRow(
                            item = item,
                            surfaceColor = surfaceColor,
                            primaryTextColor = primaryTextColor,
                            secondaryTextColor = secondaryTextColor,
                            onClickIntent = openScannerIntent
                        )
                        Spacer(modifier = GlanceModifier.height(6.dp))
                    }
                }
            }
        }
    }

    @Composable
    private fun ShoppingListItemRow(
        item: WidgetShoppingItem,
        surfaceColor: ColorProvider,
        primaryTextColor: ColorProvider,
        secondaryTextColor: ColorProvider,
        onClickIntent: Intent
    ) {
        Row(
            modifier = GlanceModifier
                .fillMaxWidth()
                .background(surfaceColor)
                .padding(8.dp)
                .clickable(actionStartActivity(onClickIntent)),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Bullet Dot
            Text(
                text = "•",
                style = TextStyle(
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = primaryTextColor
                ),
                modifier = GlanceModifier.padding(end = 6.dp)
            )

            Column(modifier = GlanceModifier.defaultWeight()) {
                Text(
                    text = item.name,
                    style = TextStyle(
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = primaryTextColor
                    ),
                    maxLines = 1
                )

                val details = listOfNotNull(item.brand, item.packageQuantity)
                    .filter { it.isNotBlank() }
                    .joinToString(" • ")

                if (details.isNotBlank()) {
                    Text(
                        text = details,
                        style = TextStyle(
                            fontSize = 10.sp,
                            color = secondaryTextColor
                        ),
                        maxLines = 1
                    )
                }
            }
        }
    }
}
