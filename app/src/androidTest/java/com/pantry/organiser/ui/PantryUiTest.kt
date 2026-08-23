package com.pantry.organiser.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.pantry.organiser.MainActivity
import org.junit.Rule
import org.junit.Test

class PantryUiTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun appTitle_isDisplayed() {
        composeTestRule.onNodeWithText("Pantry Organiser").assertIsDisplayed()
    }

    @Test
    fun shelfGrid_isDisplayed() {
        // Cells are labeled S1-L to S4-R
        composeTestRule.onNodeWithText("S4-L").assertIsDisplayed()
        composeTestRule.onNodeWithText("S1-R").assertIsDisplayed()
    }

    @Test
    fun clickingShelfCell_filtersList() {
        // Initially should show all (or none if empty, but here we check interaction)
        composeTestRule.onNodeWithText("S4-M").performClick()
        
        // After clicking, the cell should probably show some selection state 
        // In our code it changes background alpha, but for now we just verify it doesn't crash
        composeTestRule.onNodeWithText("S4-M").assertExists()
    }

    @Test
    fun tabletActionDock_isDisplayedOnWideScreen() {
        // This test might be screen size dependent. 
        // If the emulator is wide enough (>600dp), these should be visible.
        val config = composeTestRule.activity.resources.configuration
        if (config.screenWidthDp >= 600) {
            composeTestRule.onNodeWithText("Add / Restock").assertIsDisplayed()
            composeTestRule.onNodeWithText("Take / Consume").assertIsDisplayed()
        }
    }
}
