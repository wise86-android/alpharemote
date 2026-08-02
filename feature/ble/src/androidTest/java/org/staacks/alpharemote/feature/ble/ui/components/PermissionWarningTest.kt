package org.staacks.alpharemote.feature.ble.ui.components

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.staacks.alpharemote.core.ui.theme.BluetoothRemoteForSonyCamerasTheme

@RunWith(AndroidJUnit4::class)
class PermissionWarningTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun showsWarningAndButton() {
        composeTestRule.setContent {
            BluetoothRemoteForSonyCamerasTheme {
                PermissionWarning(
                    warningText = "The permission is missing.",
                    buttonText = "Grant permission",
                    onRequestClick = {},
                )
            }
        }

        composeTestRule.onNodeWithText("The permission is missing.").assertIsDisplayed()
        composeTestRule.onNodeWithText("Grant permission").assertIsDisplayed()
    }

    @Test
    fun clickingButtonInvokesCallback() {
        var clicks = 0
        composeTestRule.setContent {
            BluetoothRemoteForSonyCamerasTheme {
                PermissionWarning(
                    warningText = "The permission is missing.",
                    buttonText = "Grant permission",
                    onRequestClick = { clicks++ },
                )
            }
        }

        composeTestRule.onNodeWithText("Grant permission").performClick()

        assertEquals(1, clicks)
    }
}
