package org.staacks.alpharemote.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnySibling
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.staacks.alpharemote.R
import org.staacks.alpharemote.camera.CameraAction
import org.staacks.alpharemote.camera.CameraActionPreset
import org.staacks.alpharemote.ui.settings.SettingsViewModel.SettingsUICameraState
import org.staacks.alpharemote.ui.settings.SettingsViewModel.SettingsUIState
import org.staacks.alpharemote.ui.theme.BluetoothRemoteForSonyCamerasTheme

/**
 * Tests the settings screen as a whole: interacting with a control must invoke the callback of
 * that specific setting (and no other) with the correct value. Together with the DataStore tests
 * in org.staacks.alpharemote.data this covers that the correct setting is changed.
 */
@RunWith(AndroidJUnit4::class)
class SettingScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private class RecordedCallbacks {
        val locationUpdateChanges = mutableListOf<Boolean>()
        val broadcastControlChanges = mutableListOf<Boolean>()
        val buttonScaleChanges = mutableListOf<Int>()
        val deletedButtons = mutableListOf<Int>()
        var pairClicks = 0
        var unpairClicks = 0
        var addCustomButtonClicks = 0
    }

    private val buttons = listOf(
        CameraAction(false, null, null, null, CameraActionPreset.TRIGGER_ONCE),
        CameraAction(false, null, null, null, CameraActionPreset.RECORD),
    )

    @Composable
    private fun TestScreen(
        callbacks: RecordedCallbacks,
        updateCameraLocation: Boolean = false,
        broadcastControlEnabled: Boolean = false,
        cameraState: SettingsUICameraState = SettingsUICameraState.CONNECTED,
    ) {
        BluetoothRemoteForSonyCamerasTheme {
            SettingScreenContent(
                sectionSpacing = 16.dp,
                uiState = SettingsUIState(
                    cameraState = cameraState,
                    cameraName = "Alpha 1",
                    bluetoothEnabled = true,
                    locationServiceEnabled = true,
                ),
                updateCameraLocation = updateCameraLocation,
                customButtons = buttons,
                selectedButtonScaleIndex = 3,
                maxButtonScaleIndex = 6,
                broadcastControlEnabled = broadcastControlEnabled,
                onPairClick = { callbacks.pairClicks++ },
                onUnpairClick = { callbacks.unpairClicks++ },
                onHelpConnectionClick = {},
                onLocationUpdatesCheckedChange = callbacks.locationUpdateChanges::add,
                onAddCustomButtonClick = { callbacks.addCustomButtonClicks++ },
                onHelpCustomButtonsClick = {},
                onEditCustomButton = { _, _ -> },
                onMoveCustomButton = { _, _ -> },
                onDeleteCustomButton = callbacks.deletedButtons::add,
                onButtonScaleIndexChange = callbacks.buttonScaleChanges::add,
                onBroadcastControlCheckedChange = callbacks.broadcastControlChanges::add,
                onBroadcastMoreClick = {},
            )
        }
    }

    private fun switchWithLabel(label: String): SemanticsMatcher =
        isToggleable() and hasAnySibling(hasText(label))

    @Test
    fun togglingLocationSwitchChangesOnlyTheLocationSetting() {
        val callbacks = RecordedCallbacks()
        composeTestRule.setContent { TestScreen(callbacks, updateCameraLocation = false) }

        composeTestRule.onNode(switchWithLabel(context.getString(R.string.settings_location_send)))
            .performScrollTo()
            .performClick()

        assertEquals(listOf(true), callbacks.locationUpdateChanges)
        assertEquals(emptyList<Boolean>(), callbacks.broadcastControlChanges)
    }

    @Test
    fun togglingLocationSwitchOffReportsFalse() {
        val callbacks = RecordedCallbacks()
        composeTestRule.setContent { TestScreen(callbacks, updateCameraLocation = true) }

        composeTestRule.onNode(switchWithLabel(context.getString(R.string.settings_location_send)))
            .performScrollTo()
            .performClick()

        assertEquals(listOf(false), callbacks.locationUpdateChanges)
    }

    @Test
    fun togglingBroadcastSwitchChangesOnlyTheBroadcastSetting() {
        val callbacks = RecordedCallbacks()
        composeTestRule.setContent { TestScreen(callbacks, broadcastControlEnabled = false) }

        composeTestRule.onNode(switchWithLabel(context.getString(R.string.settings_broadcast_control_toggle)))
            .performScrollTo()
            .performClick()

        assertEquals(listOf(true), callbacks.broadcastControlChanges)
        assertEquals(emptyList<Boolean>(), callbacks.locationUpdateChanges)
    }

    @Test
    fun changingButtonSizeReportsTheNewScaleIndex() {
        val callbacks = RecordedCallbacks()
        composeTestRule.setContent { TestScreen(callbacks) }

        composeTestRule.onNodeWithText(context.getString(R.string.settings_button_size_larger))
            .performScrollTo()
            .performClick()

        assertEquals(listOf(4), callbacks.buttonScaleChanges)
    }

    @Test
    fun addCustomButtonInvokesAddCallback() {
        val callbacks = RecordedCallbacks()
        composeTestRule.setContent { TestScreen(callbacks) }

        composeTestRule.onNodeWithText(context.getString(R.string.settings_custom_buttons_add))
            .performScrollTo()
            .performClick()

        assertEquals(1, callbacks.addCustomButtonClicks)
    }

    @Test
    fun connectedCameraShowsUnpairAndInvokesUnpairCallback() {
        val callbacks = RecordedCallbacks()
        composeTestRule.setContent { TestScreen(callbacks, cameraState = SettingsUICameraState.CONNECTED) }

        composeTestRule.onNodeWithText(context.getString(R.string.settings_camera_remove))
            .performScrollTo()
            .performClick()

        assertEquals(1, callbacks.unpairClicks)
        assertEquals(0, callbacks.pairClicks)
    }

    @Test
    fun unassociatedCameraShowsPairAndInvokesPairCallback() {
        val callbacks = RecordedCallbacks()
        composeTestRule.setContent { TestScreen(callbacks, cameraState = SettingsUICameraState.NOT_ASSOCIATED) }

        composeTestRule.onNodeWithText(context.getString(R.string.settings_camera_add))
            .performScrollTo()
            .performClick()

        assertEquals(1, callbacks.pairClicks)
        assertEquals(0, callbacks.unpairClicks)
    }

    @Test
    fun showsTitleAndConfiguredCustomButtons() {
        val callbacks = RecordedCallbacks()
        composeTestRule.setContent { TestScreen(callbacks) }

        composeTestRule.onNodeWithText(context.getString(R.string.title_settings)).assertIsDisplayed()
        buttons.forEach { action ->
            composeTestRule.onNodeWithText(action.getName(context)).performScrollTo().assertIsDisplayed()
        }
    }
}
