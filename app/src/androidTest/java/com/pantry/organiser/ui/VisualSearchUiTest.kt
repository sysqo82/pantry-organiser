package com.pantry.organiser.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.pantry.organiser.MainActivity
import org.junit.Rule
import org.junit.Test

class VisualSearchUiTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun tabletVisualSearch_flowTest() {
        val config = composeTestRule.activity.resources.configuration
        // Only run on tablets
        if (config.smallestScreenWidthDp >= 600) {
            // 1. Verify Search Icon exists in TabletLayout
            val searchIcon = composeTestRule.onNodeWithContentDescription("Visual Search")
            searchIcon.assertIsDisplayed()

            // 2. Open Visual Search
            searchIcon.performClick()
            
            // 3. Verify Visual Catalog is displayed
            composeTestRule.onNodeWithText("Visual Catalog").assertIsDisplayed()
            
            // 4. Close Visual Search
            composeTestRule.onNodeWithText("Close").performClick()
            
            // 5. Verify back on Home Screen
            composeTestRule.onNodeWithText("Visual Catalog").assertDoesNotExist()
            composeTestRule.onNodeWithText("Pantry Organiser").assertIsDisplayed()
        }
    }
}
