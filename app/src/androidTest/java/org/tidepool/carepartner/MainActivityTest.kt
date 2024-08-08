package org.tidepool.carepartner

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Rule
import org.junit.Test
import kotlin.time.Duration.Companion.seconds

class MainActivityTest {
    @get:Rule
    val composeTestRule = createComposeRule()
    
    @Test
    fun loginTest() {
        composeTestRule.setContent {
            HomeUI()
        }
        composeTestRule.onNodeWithText("Log In").assertExists()
        composeTestRule.onNode(hasContentDescription("Loop Shadow Logo")).assertExists().performTouchInput {
            longClick(
                durationMillis = 2.seconds.inWholeMilliseconds
            )
        }
        composeTestRule.onNodeWithText("qa1").assertExists()
    }
}