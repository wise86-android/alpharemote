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
import org.staacks.alpharemote.data.SettingsStore
import org.staacks.alpharemote.ui.AlphaRemoteNavKey
import org.staacks.alpharemote.ui.Navigator

fun EntryProviderScope<AlphaRemoteNavKey>.cameraEntries(
    cameraViewModel: CameraViewModel,
    navigator: Navigator
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
                cameraViewModel.startAdvancedSequence()
            },
            onCustomButtonClick = { cameraViewModel.onCustomButtonClick(it) }
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
                            else -> {}
                        }
                    }
                    else -> {}
                }
            }
        }
    }
}
