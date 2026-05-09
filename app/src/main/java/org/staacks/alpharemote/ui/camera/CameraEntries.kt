package org.staacks.alpharemote.ui.camera

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.navigation3.runtime.EntryProviderScope
import org.staacks.alpharemote.R
import org.staacks.alpharemote.camera.CameraAction
import org.staacks.alpharemote.camera.CameraActionPreset
import org.staacks.alpharemote.data.SettingsStore
import org.staacks.alpharemote.ui.AlphaRemoteNavKey
import org.staacks.alpharemote.ui.Navigator

fun EntryProviderScope<AlphaRemoteNavKey>.cameraEntries(
    cameraViewModel: CameraViewModel,
    navigator: Navigator,
    onSendCameraAction: (CameraAction, Int?) -> Unit,
    onStartAdvancedSequence: (Float, Int, Float) -> Unit
) {
    entry<AlphaRemoteNavKey.Camera> {
        val uiState by cameraViewModel.uiState.collectAsState()
        var customButtons by remember { mutableStateOf(emptyList<CameraAction>()) }
        val settingsStore = SettingsStore(LocalContext.current)

        LaunchedEffect(Unit) {
            settingsStore.customButtonSettings.collect {
                customButtons = it.customButtonList ?: emptyList()
            }
        }

        CameraScreen(
            uiState = uiState,
            customButtons = customButtons,
            onGotoSettings = { navigator.navigate(AlphaRemoteNavKey.Settings) },
            onHelp = {
                navigator.navigate(
                    AlphaRemoteNavKey.Help(
                        R.string.help_camera_remote_title,
                        R.string.help_camera_remote_text
                    )
                )
            },
            onDefaultRemoteTouch = { button, action ->
                cameraViewModel.onDefaultRemoteButtonTouch(button, action)
            },
            onBulbToggleChanged = { uiState.bulbToggle = it },
            onBulbDurationChanged = { uiState.bulbDuration = it.toDoubleOrNull() },
            onIntervalToggleChanged = { uiState.intervalToggle = it },
            onIntervalCountChanged = { uiState.intervalCount = it.toIntOrNull() },
            onIntervalDurationChanged = { uiState.intervalDuration = it.toDoubleOrNull() },
            onStartSequence = {
                val bulbDuration = if (uiState.bulbToggle) { uiState.bulbDuration ?: 0.0 } else { 0.0 }
                val intervalCount = if (uiState.intervalToggle) { uiState.intervalCount ?: 1 } else { 1 }
                val intervalDuration = if (uiState.intervalToggle) { uiState.intervalDuration ?: 0.0 } else { 0.0 }
                onStartAdvancedSequence(bulbDuration.toFloat(), intervalCount, intervalDuration.toFloat())
            },
            onCustomButtonClick = { onSendCameraAction(it, null) }
        )

        // Handle CameraViewModel actions
        LaunchedEffect(cameraViewModel) {
            cameraViewModel.uiAction.collect { action ->
                when (action) {
                    is CameraViewModel.GenericCameraUIAction -> {
                        when (action.action) {
                            CameraViewModel.GenericCameraUIActionType.GOTO_DEVICE_SETTINGS -> navigator.navigate(
                                AlphaRemoteNavKey.Settings
                            )
                            CameraViewModel.GenericCameraUIActionType.HELP_REMOTE -> navigator.navigate(
                                AlphaRemoteNavKey.Help(
                                    R.string.help_camera_remote_title,
                                    R.string.help_camera_remote_text
                                )
                            )
                            CameraViewModel.GenericCameraUIActionType.START_ADVANCED_SEQUENCE -> {
                                val currentUiState = cameraViewModel.uiState.value
                                val bulbDuration = if (currentUiState.bulbToggle) {
                                    currentUiState.bulbDuration ?: 0.0
                                } else {
                                    0.0
                                }
                                val intervalCount = if (currentUiState.intervalToggle) {
                                    currentUiState.intervalCount ?: 1
                                } else {
                                    1
                                }
                                val intervalDuration = if (currentUiState.intervalToggle) {
                                    currentUiState.intervalDuration ?: 0.0
                                } else {
                                    0.0
                                }
                                onStartAdvancedSequence(
                                    bulbDuration.toFloat(),
                                    intervalCount,
                                    intervalDuration.toFloat()
                                )
                            }
                        }
                    }
                    is CameraViewModel.DefaultRemoteButtonCameraUIAction -> {
                        val preset = when (action.button) {
                            RemoteButton.SHUTTER -> CameraActionPreset.SHUTTER
                            RemoteButton.SHUTTER_HALF -> CameraActionPreset.SHUTTER_HALF
                            RemoteButton.SELFTIMER_3S -> CameraActionPreset.TRIGGER_ONCE
                            RemoteButton.RECORD -> CameraActionPreset.RECORD
                            RemoteButton.C1 -> CameraActionPreset.C1
                            RemoteButton.AF_ON -> CameraActionPreset.AF_ON
                            RemoteButton.ZOOM_IN -> CameraActionPreset.ZOOM_IN
                            RemoteButton.ZOOM_OUT -> CameraActionPreset.ZOOM_OUT
                            RemoteButton.FOCUS_FAR -> CameraActionPreset.FOCUS_FAR
                            RemoteButton.FOCUS_NEAR -> CameraActionPreset.FOCUS_NEAR
                        }
                        val selfTimer = if (action.button == RemoteButton.SELFTIMER_3S) 3.0f else null
                        onSendCameraAction(
                            CameraAction(
                                toggle = action.button in setOf(RemoteButton.SHUTTER_HALF, RemoteButton.AF_ON),
                                selfTimer = selfTimer,
                                duration = null,
                                step = null,
                                preset = preset
                            ), action.event
                        )
                    }
                }
            }
        }
    }
}
