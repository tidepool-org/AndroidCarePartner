package org.tidepool.carepartner

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.tidepool.carepartner.backend.PillData
import org.tidepool.carepartner.ui.theme.LoopFollowTheme
import org.tidepool.sdk.model.confirmations.Confirmation
import org.tidepool.sdk.model.metadata.Profile
import kotlin.reflect.KProperty

class FollowUITest {
    
    class UiResource : ExternalResource() {
        
        private lateinit var ui: FollowUI
        override fun before() {
            ui = FollowUI()
        }
        
        override fun after() {
            ui.future?.cancel(true)
        }
        
        operator fun getValue(followUITest: FollowUITest, property: KProperty<*>): FollowUI {
            return ui
        }
    }
    
    @get:Rule
    val composeTestRule = createComposeRule()
    
    @get:Rule
    val uiRule = UiResource()
    private val ui by uiRule
    
    @Test
    fun invitationsTest() {
        createApp()
        composeTestRule.onNodeWithText("Invitations").assertExists().performClick()
        composeTestRule.onNodeWithText("Check for new invites").assertExists()
        composeTestRule.onNodeWithText("You have no pending invites yet").assertExists()
    }
    
    @Test
    fun singleFollowerCardTest() {
        createApp(
            PillData(name = "Mango")
        )
        
        // Assert that the visible card is expanded
        composeTestRule.onNodeWithText("Change in Glucose").assertExists()
        composeTestRule.onNodeWithText("Active Insulin").assertExists()
        composeTestRule.onNodeWithText("Active Carbs").assertExists().performClick()
        
        // Assert that they are not visible after the click
        composeTestRule.onNodeWithText("Change in Glucose").assertDoesNotExist()
        composeTestRule.onNodeWithText("Active Insulin").assertDoesNotExist()
        composeTestRule.onNodeWithText("Active Carbs").assertDoesNotExist()
        
        composeTestRule.onNodeWithText("Mango").assertExists().performClick()
        
        // Assert that they exist again after clicking on the user's name
        composeTestRule.onNodeWithText("Change in Glucose").assertExists()
        composeTestRule.onNodeWithText("Active Insulin").assertExists()
        composeTestRule.onNodeWithText("Active Carbs").assertExists()
        
        // Assert that the invitations exist, but that it does NOT show the pending invites test
        composeTestRule.onNodeWithText("Invitations").assertExists().performClick()
        composeTestRule.onNodeWithText("Check for new invites").assertExists()
        composeTestRule.onNodeWithText("You have no pending invites yet").assertDoesNotExist()
    }
    
    @Test
    fun multipleFollowerCardTest() {
        createApp(
            PillData(name = "Angel"),
            PillData(name = "Willow")
        )
        
        composeTestRule.onNodeWithText("Angel").assertExists()
        composeTestRule.onNodeWithText("Willow").assertExists()
        
        // Assert that we cannot an expanded card
        composeTestRule.onNodeWithText("Change in Glucose").assertDoesNotExist()
        composeTestRule.onNodeWithText("Active Insulin").assertDoesNotExist()
        composeTestRule.onNodeWithText("Active Carbs").assertDoesNotExist()
        
        composeTestRule.onNodeWithText("Willow").performClick()
        
        // Assert that the expanded card is visible
        composeTestRule.onNodeWithText("Change in Glucose").assertExists()
        composeTestRule.onNodeWithText("Active Insulin").assertExists()
        composeTestRule.onNodeWithText("Active Carbs").assertExists()
        
        composeTestRule.onNodeWithText("Willow").performClick()
        
        // Assert that we cannot an expanded card
        composeTestRule.onNodeWithText("Change in Glucose").assertDoesNotExist()
        composeTestRule.onNodeWithText("Active Insulin").assertDoesNotExist()
        composeTestRule.onNodeWithText("Active Carbs").assertDoesNotExist()
        
        composeTestRule.onNodeWithText("Angel").performClick()
        
        // Assert that the expanded card is visible
        composeTestRule.onNodeWithText("Change in Glucose").assertExists()
        composeTestRule.onNodeWithText("Active Insulin").assertExists()
        composeTestRule.onNodeWithText("Active Carbs").assertExists()
        
        composeTestRule.onNodeWithText("Angel").performClick()
        
        composeTestRule.onNodeWithText("Change in Glucose").assertDoesNotExist()
        composeTestRule.onNodeWithText("Active Insulin").assertDoesNotExist()
        composeTestRule.onNodeWithText("Active Carbs").assertDoesNotExist()
    }
    
    private fun createInvitations(vararg names: String): Array<Confirmation> {
        return names.map {
            Confirmation(
                creator = Confirmation.Creator(
                    profile = Profile(
                        fullName = it
                    )
                )
            )
        }.toTypedArray()
    }
    
    private fun createApp(vararg data: PillData) {
        composeTestRule.setContent {
            LoopFollowTheme {
                ui.App(
                    allData = data
                )
            }
        }
    }
}