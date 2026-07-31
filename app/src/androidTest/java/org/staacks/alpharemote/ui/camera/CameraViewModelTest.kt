package org.staacks.alpharemote.ui.camera

import android.app.Application
import androidx.datastore.preferences.core.edit
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.staacks.alpharemote.camera.CameraAction
import org.staacks.alpharemote.camera.CameraActionPreset
import org.staacks.alpharemote.data.AppearanceSettings
import org.staacks.alpharemote.data.settingsDataStore

@RunWith(AndroidJUnit4::class)
class CameraViewModelTest {

    private val application = ApplicationProvider.getApplicationContext<Application>()
    private val appearanceSettings = AppearanceSettings(application)

    private val timeoutMs = 10_000L

    @Before
    fun clearDataStore() {
        runBlocking {
            application.settingsDataStore.edit { it.clear() }
        }
    }

    @Test
    fun initialStateIsDisconnectedWithDefaults() {
        val viewModel = CameraViewModel(application)

        val state = viewModel.uiState.value
        assertFalse(state.connected)
        assertNull(state.cameraState)
        assertFalse(state.bulbToggle)
        assertFalse(state.intervalToggle)
        assertEquals("5.0", state.bulbDuration)
        assertEquals("50", state.intervalCount)
        assertEquals("3.0", state.intervalDuration)
    }

    @Test
    fun bulbSettersUpdateOnlyTheBulbFields() {
        val viewModel = CameraViewModel(application)

        viewModel.setBulbToggle(true)
        viewModel.setBulbDuration("12.5")

        val state = viewModel.uiState.value
        assertEquals(true, state.bulbToggle)
        assertEquals("12.5", state.bulbDuration)
        assertFalse(state.intervalToggle)
        assertEquals("50", state.intervalCount)
        assertEquals("3.0", state.intervalDuration)
    }

    @Test
    fun intervalSettersUpdateOnlyTheIntervalFields() {
        val viewModel = CameraViewModel(application)

        viewModel.setIntervalToggle(true)
        viewModel.setIntervalCount("10")
        viewModel.setIntervalDuration("2.0")

        val state = viewModel.uiState.value
        assertEquals(true, state.intervalToggle)
        assertEquals("10", state.intervalCount)
        assertEquals("2.0", state.intervalDuration)
        assertFalse(state.bulbToggle)
        assertEquals("5.0", state.bulbDuration)
    }

    @Test
    fun nonNumericInputIsKeptAsRawString() {
        val viewModel = CameraViewModel(application)

        viewModel.setBulbDuration("abc")
        viewModel.setIntervalCount("")

        val state = viewModel.uiState.value
        assertEquals("abc", state.bulbDuration)
        assertEquals("", state.intervalCount)
    }

    @Test
    fun customButtonsAreEmptyWithoutStoredList() = runBlocking {
        val viewModel = CameraViewModel(application)

        val buttons = withTimeout(timeoutMs) { viewModel.customButtons.first() }

        assertEquals(emptyList<CameraAction>(), buttons)
    }

    @Test
    fun customButtonsReflectTheStoredList() = runBlocking {
        val viewModel = CameraViewModel(application)
        val list = listOf(
            CameraAction(false, null, null, null, CameraActionPreset.TRIGGER_ONCE),
            CameraAction(true, null, 1.0f, 0.5f, CameraActionPreset.ZOOM_IN),
        )

        appearanceSettings.saveCustomButtonList(list)

        val buttons = withTimeout(timeoutMs) { viewModel.customButtons.first { it == list } }
        assertEquals(list, buttons)
    }

    @Test
    fun gotoDeviceSettingsEmitsMatchingAction() {
        assertEquals(
            CameraViewModel.GenericCameraUIActionType.GOTO_DEVICE_SETTINGS,
            firstGenericUiActionAfter { it.gotoDeviceSettings() },
        )
    }

    @Test
    fun helpRemoteEmitsMatchingAction() {
        assertEquals(
            CameraViewModel.GenericCameraUIActionType.HELP_REMOTE,
            firstGenericUiActionAfter { it.helpRemote() },
        )
    }

    private fun firstGenericUiActionAfter(
        trigger: (CameraViewModel) -> Unit,
    ): CameraViewModel.GenericCameraUIActionType = runBlocking {
        val viewModel = CameraViewModel(application)
        // UNDISPATCHED so the collector is subscribed before the action is triggered — the
        // shared flow has no replay.
        val action = async(start = CoroutineStart.UNDISPATCHED) { viewModel.uiAction.first() }
        trigger(viewModel)
        val result = withTimeout(timeoutMs) { action.await() }
        (result as CameraViewModel.GenericCameraUIAction).action
    }
}
