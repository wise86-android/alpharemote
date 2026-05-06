package org.staacks.alpharemote.ui

import android.content.IntentSender
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.staacks.alpharemote.R
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.scene.DialogSceneStrategy
import androidx.navigation3.ui.NavDisplay
import android.companion.CompanionDeviceManager
import androidx.compose.material3.MaterialTheme
import org.staacks.alpharemote.camera.CameraAction
import org.staacks.alpharemote.camera.CameraActionPreset
import org.staacks.alpharemote.data.SettingsStore
import org.staacks.alpharemote.service.AlphaRemoteService
import org.staacks.alpharemote.ui.about.AboutScreen
import org.staacks.alpharemote.ui.camera.CameraScreen
import org.staacks.alpharemote.ui.camera.CameraViewModel
import org.staacks.alpharemote.ui.camera.RemoteButton
import org.staacks.alpharemote.ui.settings.CameraActionPicker
import org.staacks.alpharemote.ui.settings.CameraActionPickerContent
import org.staacks.alpharemote.ui.settings.CompanionDeviceHelper
import org.staacks.alpharemote.ui.settings.SettingScreen
import org.staacks.alpharemote.ui.settings.SettingsViewModel

@Composable
fun MainScreen(
    onPairRequested: (IntentSender) -> Unit,
    onOpenUrl: (String) -> Unit,
    onSendCameraAction: (CameraAction, Int?) -> Unit,
    onStartAdvancedSequence: (Float, Int, Float) -> Unit
) {
    val topLevelRoutes = setOf(
        AlphaRemoteNavKey.Camera,
        AlphaRemoteNavKey.Settings,
        AlphaRemoteNavKey.About
    )

    val navigationState = rememberNavigationState(
        startRoute = AlphaRemoteNavKey.Camera,
        topLevelRoutes = topLevelRoutes
    )

    val navigator = remember { Navigator(navigationState) }

    val cameraViewModel: CameraViewModel = viewModel()
    val settingsViewModel: SettingsViewModel = viewModel()

    val entryProvider = entryProvider {
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
                    navigator.navigate(AlphaRemoteNavKey.Help(
                        R.string.help_camera_remote_title,
                        R.string.help_camera_remote_text
                    ))
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
                                CameraViewModel.GenericCameraUIActionType.GOTO_DEVICE_SETTINGS -> navigator.navigate(AlphaRemoteNavKey.Settings)
                                CameraViewModel.GenericCameraUIActionType.HELP_REMOTE -> navigator.navigate(AlphaRemoteNavKey.Help(
                                    R.string.help_camera_remote_title,
                                    R.string.help_camera_remote_text
                                ))
                                CameraViewModel.GenericCameraUIActionType.START_ADVANCED_SEQUENCE -> {
                                    val currentUiState = cameraViewModel.uiState.value
                                    val bulbDuration = if (currentUiState.bulbToggle) { currentUiState.bulbDuration ?: 0.0 } else { 0.0 }
                                    val intervalCount = if (currentUiState.intervalToggle) { currentUiState.intervalCount ?: 1 } else { 1 }
                                    val intervalDuration = if (currentUiState.intervalToggle) { currentUiState.intervalDuration ?: 0.0 } else { 0.0 }
                                    onStartAdvancedSequence(bulbDuration.toFloat(), intervalCount, intervalDuration.toFloat())
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
                            onSendCameraAction(CameraAction(
                                toggle = action.button in setOf(RemoteButton.SHUTTER_HALF, RemoteButton.AF_ON),
                                selfTimer = selfTimer,
                                duration = null,
                                step = null,
                                preset = preset
                            ), action.event)
                        }
                    }
                }
            }
        }

        entry<AlphaRemoteNavKey.Settings> {
            val context = LocalContext.current
            SettingScreen(
                settingsViewModel = settingsViewModel,
                onPairRequested = {
                    CompanionDeviceHelper.pairCompanionDevice(context, object : CompanionDeviceManager.Callback() {
                        @Deprecated("Deprecated in Java")
                        override fun onDeviceFound(chooserLauncher: IntentSender) {
                            onPairRequested(chooserLauncher)
                        }
                        override fun onFailure(error: CharSequence?) {
                            settingsViewModel.reportErrorState(error.toString())
                        }
                    })
                },
                onUnpairRequested = {
                    CompanionDeviceHelper.unpairCompanionDevice(context)
                    AlphaRemoteService.sendDisconnectIntent(context)
                },
                onAddCustomButtonRequested = {
                    navigator.navigate(AlphaRemoteNavKey.CameraActionPicker(-1, CameraAction(false, null, null, null, CameraActionPreset.STOP), false))
                },
                onHelpConnectionRequested = {
                    navigator.navigate(AlphaRemoteNavKey.Help(
                        R.string.help_settings_connection_troubleshooting_title,
                        R.string.help_settings_connection_troubleshooting_text
                    ))
                },
                onHelpCustomButtonsRequested = {
                    navigator.navigate(AlphaRemoteNavKey.Help(
                        R.string.help_settings_custom_buttons_title,
                        R.string.help_settings_custom_buttons_text
                    ))
                },
                onEditCustomButton = { index, action ->
                    navigator.navigate(AlphaRemoteNavKey.CameraActionPicker(index, action, true))
                },
                onOpenUrl = onOpenUrl
            )
        }

        entry<AlphaRemoteNavKey.About> {
            AboutScreen(onOpenUrl = onOpenUrl)
        }

        entry<AlphaRemoteNavKey.Help>(metadata = DialogSceneStrategy.dialog()) { key ->
            AlertDialog(
                onDismissRequest = { navigator.goBack() },
                title = { Text(stringResource(key.titleRes)) },
                text = { Text(stringResource(key.textRes)) },
                confirmButton = {
                    Button(onClick = { navigator.goBack() }) {
                        Text("OK")
                    }
                }
            )
        }

        entry<AlphaRemoteNavKey.CameraActionPicker>(metadata = DialogSceneStrategy.dialog()) { key ->
            AlertDialog(
                onDismissRequest = { navigator.goBack() },
                text = {
                    Surface(
                        shape = RoundedCornerShape(28.dp),
                        color = MaterialTheme.colorScheme.surface,
                        tonalElevation = 6.dp
                    ) {
                        CameraActionPickerContent(
                            startAction = key.action,
                            showDelete = key.showDelete,
                            selftimerSeekBarTimeMap = remember { CameraActionPicker.SeekBarTimeMap(10, 600) },
                            holdSeekBarTimeMap = remember { CameraActionPicker.SeekBarTimeMap(0, 100) },
                            onCancel = { navigator.goBack() },
                            onDelete = {
                                settingsViewModel.removeCustomButton(key.index)
                                navigator.goBack()
                            },
                            onSave = { action ->
                                settingsViewModel.updateCustomButton(key.index, action)
                                navigator.goBack()
                            }
                        )
                    }
                },
                confirmButton = {},
                dismissButton = {}
            )
        }
    }

    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = navigationState.topLevelRoute == AlphaRemoteNavKey.Camera,
                    onClick = { navigator.navigate(AlphaRemoteNavKey.Camera) },
                    icon = { Icon(Icons.Default.PhotoCamera, null) },
                    label = { Text(stringResource(R.string.title_camera)) }
                )
                NavigationBarItem(
                    selected = navigationState.topLevelRoute == AlphaRemoteNavKey.Settings,
                    onClick = { navigator.navigate(AlphaRemoteNavKey.Settings) },
                    icon = { Icon(Icons.Default.Settings, null) },
                    label = { Text(stringResource(R.string.title_settings)) }
                )
                NavigationBarItem(
                    selected = navigationState.topLevelRoute == AlphaRemoteNavKey.About,
                    onClick = { navigator.navigate(AlphaRemoteNavKey.About) },
                    icon = { Icon(Icons.Default.Info, null) },
                    label = { Text(stringResource(R.string.title_about)) }
                )
            }
        }
    ) { padding ->
        NavDisplay(
            modifier = Modifier.padding(padding).fillMaxSize(),
            entries = navigationState.toEntries(entryProvider),
            onBack = { navigator.goBack() },
            sceneStrategies = listOf(DialogSceneStrategy())
        )
    }
}
