package org.staacks.alpharemote.feature.ble.ui.components

import androidx.compose.material3.Text
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.staacks.alpharemote.core.ui.theme.BluetoothRemoteForSonyCamerasTheme

@RunWith(AndroidJUnit4::class)
class SettingsSectionTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun showsTitleDescriptionAndContent() {
        composeTestRule.setContent {
            BluetoothRemoteForSonyCamerasTheme {
                SettingsSection(
                    title = "Section title",
                    description = "Section description",
                ) {
                    Text(text = "Section content")
                }
            }
        }

        composeTestRule.onNodeWithText("Section title").assertIsDisplayed()
        composeTestRule.onNodeWithText("Section description").assertIsDisplayed()
        composeTestRule.onNodeWithText("Section content").assertIsDisplayed()
    }

    @Test
    fun omitsDescriptionWhenNull() {
        composeTestRule.setContent {
            BluetoothRemoteForSonyCamerasTheme {
                SettingsSection(title = "Section title") {
                    Text(text = "Section content")
                }
            }
        }

        composeTestRule.onNodeWithText("Section title").assertIsDisplayed()
        composeTestRule.onNodeWithText("Section content").assertIsDisplayed()
        composeTestRule.onNodeWithText("Section description").assertDoesNotExist()
    }
}
