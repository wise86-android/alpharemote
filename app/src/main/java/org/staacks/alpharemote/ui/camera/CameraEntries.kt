package org.staacks.alpharemote.ui.camera

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.EntryProviderScope
import org.staacks.alpharemote.R
import org.staacks.alpharemote.ui.AlphaRemoteNavKey
import org.staacks.alpharemote.ui.Navigator

fun EntryProviderScope<AlphaRemoteNavKey>.cameraEntries(
    cameraViewModel: CameraViewModel,
    navigator: Navigator
) {
    entry<AlphaRemoteNavKey.Camera> {
        val uiState by cameraViewModel.uiState.collectAsStateWithLifecycle()
        val customButtons by cameraViewModel.customButtons.collectAsStateWithLifecycle()

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
            onBulbToggleChanged = cameraViewModel::setBulbToggle,
            onBulbDurationChanged = cameraViewModel::setBulbDuration,
            onIntervalToggleChanged = cameraViewModel::setIntervalToggle,
            onIntervalCountChanged = cameraViewModel::setIntervalCount,
            onIntervalDurationChanged = cameraViewModel::setIntervalDuration,
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
