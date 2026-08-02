package org.staacks.alpharemote.feature.ble.ui.settings
import org.staacks.alpharemote.feature.ble.R

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.staacks.alpharemote.core.ui.theme.BluetoothRemoteForSonyCamerasTheme

@RunWith(AndroidJUnit4::class)
class BroadcastControlSettingsTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun showsTitleToggleAndDocumentationButton() {
        composeTestRule.setContent {
            BluetoothRemoteForSonyCamerasTheme {
                BroadcastControlSettings(
                    enabled = true,
                    onCheckedChange = {},
                    onMoreClick = {},
                )
            }
        }

        composeTestRule.onNodeWithText(context.getString(R.string.settings_broadcast_control))
            .assertIsDisplayed()
        composeTestRule.onNodeWithText(context.getString(R.string.settings_broadcast_control_toggle))
            .assertIsDisplayed()
        composeTestRule.onNode(isToggleable()).assertIsOn()
        composeTestRule.onNodeWithText(context.getString(R.string.settings_broadcast_control_more_label))
            .assertIsDisplayed()
    }

    @Test
    fun togglingSwitchReportsNewValue() {
        val reported = mutableListOf<Boolean>()
        composeTestRule.setContent {
            BluetoothRemoteForSonyCamerasTheme {
                BroadcastControlSettings(
                    enabled = false,
                    onCheckedChange = reported::add,
                    onMoreClick = {},
                )
            }
        }

        composeTestRule.onNode(isToggleable()).performClick()

        assertEquals(listOf(true), reported)
    }

    @Test
    fun clickingDocumentationInvokesMoreCallback() {
        var moreClicks = 0
        composeTestRule.setContent {
            BluetoothRemoteForSonyCamerasTheme {
                BroadcastControlSettings(
                    enabled = false,
                    onCheckedChange = {},
                    onMoreClick = { moreClicks++ },
                )
            }
        }

        composeTestRule.onNodeWithText(context.getString(R.string.settings_broadcast_control_more_label))
            .performClick()

        assertEquals(1, moreClicks)
    }
}
