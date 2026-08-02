package org.staacks.alpharemote.feature.ble.ui.camera

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
import org.staacks.alpharemote.feature.ble.camera.CameraAction
import org.staacks.alpharemote.feature.ble.camera.CameraActionPreset
import org.staacks.alpharemote.feature.ble.data.AppearanceSettings
import org.staacks.alpharemote.feature.ble.data.settingsDataStore

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
    fun initialStateIsDisconnected() {
        val viewModel = CameraViewModel(application)

        val state = viewModel.uiState.value
        assertFalse(state.connected)
        assertNull(state.cameraState)
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
            CameraAction(false, null, CameraActionPreset.TRIGGER_ONCE),
            CameraAction(true, 0.5f, CameraActionPreset.ZOOM_IN),
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
