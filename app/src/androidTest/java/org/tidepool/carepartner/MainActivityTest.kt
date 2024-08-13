package org.tidepool.carepartner

import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
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
        composeTestRule.onNode(hasContentDescription("Loop Shadow Logo")).assertExists()
            .performTouchInput {
                longClick(
                    durationMillis = 2.seconds.inWholeMilliseconds
                )
            }
        composeTestRule.onNodeWithText("qa1").assertExists()
    }
}