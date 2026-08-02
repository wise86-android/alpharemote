package org.staacks.alpharemote.feature.ble.ui.settings
import org.staacks.alpharemote.feature.ble.R

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.staacks.alpharemote.feature.ble.ui.settings.SettingsViewModel.SettingsUICameraState
import org.staacks.alpharemote.feature.ble.ui.settings.SettingsViewModel.SettingsUIState
import org.staacks.alpharemote.core.ui.theme.BluetoothRemoteForSonyCamerasTheme

@RunWith(AndroidJUnit4::class)
class CameraSettingsSectionTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private fun setContent(
        state: SettingsUIState,
        onPairClick: () -> Unit = {},
        onUnpairClick: () -> Unit = {},
        onHelpClick: () -> Unit = {},
    ) {
        composeTestRule.setContent {
            BluetoothRemoteForSonyCamerasTheme {
                CameraSettingsSection(
                    state = state,
                    onPairClick = onPairClick,
                    onUnpairClick = onUnpairClick,
                    onHelpClick = onHelpClick,
                )
            }
        }
    }

    @Test
    fun notAssociatedShowsHintAndPairButton() {
        var pairClicks = 0
        setContent(
            state = SettingsUIState(
                cameraState = SettingsUICameraState.NOT_ASSOCIATED,
                bluetoothEnabled = true,
            ),
            onPairClick = { pairClicks++ },
        )

        composeTestRule.onNodeWithText(context.getString(R.string.settings_camera_not_associated))
            .assertIsDisplayed()
        composeTestRule.onNodeWithText(context.getString(R.string.settings_camera_remove))
            .assertDoesNotExist()

        composeTestRule.onNodeWithText(context.getString(R.string.settings_camera_add))
            .assertIsDisplayed()
            .performClick()
        assertEquals(1, pairClicks)
    }

    @Test
    fun connectedShowsCameraNameAndUnpairButton() {
        var unpairClicks = 0
        setContent(
            state = SettingsUIState(
                cameraState = SettingsUICameraState.CONNECTED,
                cameraName = "Alpha 1",
                bluetoothEnabled = true,
            ),
            onUnpairClick = { unpairClicks++ },
        )

        composeTestRule.onNodeWithText(context.getString(R.string.settings_camera_connected, "Alpha 1"))
            .assertIsDisplayed()
        composeTestRule.onNodeWithText(context.getString(R.string.settings_camera_add))
            .assertDoesNotExist()

        composeTestRule.onNodeWithText(context.getString(R.string.settings_camera_remove))
            .assertIsDisplayed()
            .performClick()
        assertEquals(1, unpairClicks)
    }

    @Test
    fun offlineShowsStoredCameraName() {
        setContent(
            state = SettingsUIState(
                cameraState = SettingsUICameraState.OFFLINE,
                cameraName = "Alpha 1",
                bluetoothEnabled = true,
            ),
        )

        composeTestRule.onNodeWithText(context.getString(R.string.settings_camera_offline, "Alpha 1"))
            .assertIsDisplayed()
    }

    @Test
    fun errorStateShowsErrorMessage() {
        setContent(
            state = SettingsUIState(
                cameraState = SettingsUICameraState.ERROR,
                cameraError = "something failed",
                bluetoothEnabled = true,
            ),
        )

        composeTestRule.onNodeWithText(context.getString(R.string.settings_camera_error, "something failed"))
            .assertIsDisplayed()
    }

    @Test
    fun disabledBluetoothShowsWarning() {
        setContent(
            state = SettingsUIState(
                cameraState = SettingsUICameraState.NOT_ASSOCIATED,
                bluetoothEnabled = false,
            ),
        )

        composeTestRule.onNodeWithText(context.getString(R.string.settings_bluetooth_disabled))
            .assertIsDisplayed()
    }

    @Test
    fun helpButtonInvokesCallback() {
        var helpClicks = 0
        setContent(
            state = SettingsUIState(
                cameraState = SettingsUICameraState.NOT_ASSOCIATED,
                bluetoothEnabled = true,
            ),
            onHelpClick = { helpClicks++ },
        )

        composeTestRule.onNodeWithText(context.getString(R.string.help)).performClick()

        assertEquals(1, helpClicks)
    }
}
