package org.staacks.alpharemote.ui.settings

import android.app.Application
import androidx.datastore.preferences.core.edit
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.staacks.alpharemote.camera.CameraAction
import org.staacks.alpharemote.camera.CameraActionPreset
import org.staacks.alpharemote.data.AppearanceSettings
import org.staacks.alpharemote.data.BehaviorSettings
import org.staacks.alpharemote.data.settingsDataStore

/**
 * Tests SettingsViewModel against the real DataStore: every mutation must be observable both
 * through the ViewModel's own flows and as the correctly persisted setting.
 */
@RunWith(AndroidJUnit4::class)
class SettingsViewModelTest {

    private val application = ApplicationProvider.getApplicationContext<Application>()
    private val appearanceSettings = AppearanceSettings(application)
    private val behaviorSettings = BehaviorSettings(application)

    private val timeoutMs = 10_000L

    @Before
    fun clearDataStore() {
        runBlocking {
            application.settingsDataStore.edit { it.clear() }
        }
    }

    /**
     * Awaits a flow value matching [predicate], robust against a DataStore race: a `.data`
     * collection whose subscription starts while a write is still in flight can miss the update
     * notification and then never re-emits, even though the write itself lands. The ViewModel
     * persists asynchronously, so awaits here inherently race against the write. On a miss,
     * wait out the stateIn WhileSubscribed grace period so the upstream DataStore collection is
     * restarted, then resubscribe — a fresh subscription always reads the persisted state.
     */
    private suspend fun <T> awaitFlow(label: String, flow: Flow<T>, predicate: (T) -> Boolean): T {
        withTimeoutOrNull(2_000) { flow.first { predicate(it) } }?.let { return it }
        delay(5_500)
        return await(label) { flow.first { predicate(it) } }
    }

    /** withTimeout with a label naming the awaited condition, so a hang is identifiable. */
    private suspend fun <T> await(label: String, block: suspend () -> T): T =
        withTimeoutOrNull(timeoutMs) { block() }
            ?: throw AssertionError("Timed out awaiting: $label")

    /**
     * The ViewModel's init block seeds the default button list asynchronously. Every test must
     * wait for that seed to land before proceeding — and before finishing: a seed still pending
     * when the test ends would fire after the next test cleared the store and overwrite that
     * test's own writes.
     */
    private suspend fun awaitSeededButtonList(): List<CameraAction> =
        awaitFlow("seeded button list", appearanceSettings.customButtonSettings) {
            it.customButtonList != null
        }.customButtonList!!

    private suspend fun createSeededViewModel(): SettingsViewModel {
        val viewModel = SettingsViewModel(application)
        awaitSeededButtonList()
        return viewModel
    }

    @Test
    fun initSeedsDefaultCustomButtonsOnFirstRun() = runBlocking {
        val viewModel = SettingsViewModel(application)

        val seeded = awaitSeededButtonList()

        assertEquals(seeded, viewModel.customButtonListFlow.value)
        assertTrue(seeded.isNotEmpty())
    }

    @Test
    fun setUpdateCameraLocationPersistsTheLocationSetting() = runBlocking {
        val viewModel = createSeededViewModel()

        viewModel.setUpdateCameraLocation(true)

        awaitFlow("persisted location flag", behaviorSettings.updateCameraLocation) { it }
        assertEquals(false, behaviorSettings.getBroadcastControl())
    }

    @Test
    fun setBroadcastControlPersistsAndIsReflectedInTheViewModelFlow() = runBlocking {
        val viewModel = createSeededViewModel()

        viewModel.setBroadcastControl(true)

        awaitFlow("broadcastControl flow", viewModel.broadcastControl) { it }
        assertTrue(behaviorSettings.getBroadcastControl())
        assertEquals(false, behaviorSettings.updateCameraLocation.first())
    }

    @Test
    fun setButtonScaleIndexPersistsTheMatchingScale() = runBlocking {
        val viewModel = createSeededViewModel()

        viewModel.setButtonScaleIndex(5)

        awaitFlow("buttonScaleIndex == 5", viewModel.buttonScaleIndex) { it == 5 }
        assertEquals(viewModel.buttonScaleSteps[5], appearanceSettings.getNotificationButtonSize())
    }

    @Test
    fun setButtonScaleIndexCoercesOutOfRangeValues() = runBlocking {
        val viewModel = createSeededViewModel()

        viewModel.setButtonScaleIndex(99)

        val lastIndex = viewModel.buttonScaleSteps.lastIndex
        awaitFlow("buttonScaleIndex == lastIndex", viewModel.buttonScaleIndex) { it == lastIndex }
        assertEquals(viewModel.buttonScaleSteps.last(), appearanceSettings.getNotificationButtonSize())
    }

    @Test
    fun removeCustomButtonDeletesExactlyTheRequestedIndex() = runBlocking {
        val viewModel = SettingsViewModel(application)
        val seeded = awaitSeededButtonList()

        viewModel.removeCustomButton(1)

        val expected = seeded.toMutableList().apply { removeAt(1) }
        awaitFlow("customButtonListFlow == expected", viewModel.customButtonListFlow) { it == expected }
        assertEquals(expected, appearanceSettings.getCustomButtonList())
    }

    @Test
    fun moveCustomButtonReordersTheList() = runBlocking {
        val viewModel = SettingsViewModel(application)
        val seeded = awaitSeededButtonList()

        viewModel.moveCustomButton(0, 2)

        val expected = seeded.toMutableList().apply { add(2, removeAt(0)) }
        awaitFlow("customButtonListFlow == expected", viewModel.customButtonListFlow) { it == expected }
        assertEquals(expected, appearanceSettings.getCustomButtonList())
    }

    @Test
    fun updateCustomButtonWithNegativeIndexAppends() = runBlocking {
        val viewModel = SettingsViewModel(application)
        val seeded = awaitSeededButtonList()
        val newAction = CameraAction(true, null, CameraActionPreset.ZOOM_IN)

        viewModel.updateCustomButton(-1, newAction)

        val expected = seeded + newAction
        awaitFlow("customButtonListFlow == expected", viewModel.customButtonListFlow) { it == expected }
        assertEquals(expected, appearanceSettings.getCustomButtonList())
    }

    @Test
    fun updateCustomButtonReplacesTheRequestedIndex() = runBlocking {
        val viewModel = SettingsViewModel(application)
        val seeded = awaitSeededButtonList()
        val newAction = CameraAction(false, null, CameraActionPreset.SHUTTER)

        viewModel.updateCustomButton(0, newAction)

        val expected = seeded.toMutableList().apply { set(0, newAction) }
        awaitFlow("customButtonListFlow == expected", viewModel.customButtonListFlow) { it == expected }
        assertEquals(expected, appearanceSettings.getCustomButtonList())
    }

    @Test
    fun updateCustomButtonIgnoresAnOutOfRangeIndex() = runBlocking {
        val viewModel = SettingsViewModel(application)
        val seeded = awaitSeededButtonList()

        viewModel.updateCustomButton(seeded.size, CameraAction(false, null, CameraActionPreset.SHUTTER))

        // The list must stay unchanged; give a potential (faulty) write a moment to land.
        delay(500)
        assertEquals(seeded, appearanceSettings.getCustomButtonList())
    }

    @Test
    fun unpairEmitsUnpairAction() {
        assertEquals(SettingsViewModel.SettingsUIAction.UNPAIR, firstUiActionAfter { it.unpair() })
    }

    @Test
    fun addCustomButtonEmitsAddAction() {
        assertEquals(SettingsViewModel.SettingsUIAction.ADD_CUSTOM_BUTTON, firstUiActionAfter { it.addCustomButton() })
    }

    @Test
    fun helpConnectionEmitsHelpConnectionAction() {
        assertEquals(SettingsViewModel.SettingsUIAction.HELP_CONNECTION, firstUiActionAfter { it.helpConnection() })
    }

    @Test
    fun helpCustomButtonsEmitsHelpCustomButtonsAction() {
        assertEquals(SettingsViewModel.SettingsUIAction.HELP_CUSTOM_BUTTONS, firstUiActionAfter { it.helpCustomButtons() })
    }

    @Test
    fun openBluetoothSettingsEmitsOpenBluetoothSettingsAction() {
        assertEquals(SettingsViewModel.SettingsUIAction.OPEN_BLUETOOTH_SETTINGS, firstUiActionAfter { it.openBluetoothSettings() })
    }

    private fun firstUiActionAfter(
        trigger: (SettingsViewModel) -> Unit,
    ): SettingsViewModel.SettingsUIAction = runBlocking {
        val viewModel = createSeededViewModel()
        // UNDISPATCHED so the collector is subscribed before the action is triggered — the
        // shared flow has no replay.
        val action = async(start = CoroutineStart.UNDISPATCHED) { viewModel.uiAction.first() }
        trigger(viewModel)
        await("uiAction emission") { action.await() }
    }
}
