package org.staacks.alpharemote.ui.components

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.staacks.alpharemote.ui.theme.BluetoothRemoteForSonyCamerasTheme

@RunWith(AndroidJUnit4::class)
class LabeledSwitchRowTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun showsLabelAndCheckedState() {
        composeTestRule.setContent {
            BluetoothRemoteForSonyCamerasTheme {
                LabeledSwitchRow(
                    label = "Enable feature",
                    checked = true,
                    onCheckedChange = {},
                )
            }
        }

        composeTestRule.onNodeWithText("Enable feature").assertIsDisplayed()
        composeTestRule.onNode(isToggleable()).assertIsOn()
    }

    @Test
    fun showsUncheckedState() {
        composeTestRule.setContent {
            BluetoothRemoteForSonyCamerasTheme {
                LabeledSwitchRow(
                    label = "Enable feature",
                    checked = false,
                    onCheckedChange = {},
                )
            }
        }

        composeTestRule.onNode(isToggleable()).assertIsOff()
    }

    @Test
    fun clickingSwitchReportsToggledValue() {
        val reportedValues = mutableListOf<Boolean>()
        composeTestRule.setContent {
            BluetoothRemoteForSonyCamerasTheme {
                LabeledSwitchRow(
                    label = "Enable feature",
                    checked = false,
                    onCheckedChange = reportedValues::add,
                )
            }
        }

        composeTestRule.onNode(isToggleable()).performClick()

        assertEquals(listOf(true), reportedValues)
    }
}
