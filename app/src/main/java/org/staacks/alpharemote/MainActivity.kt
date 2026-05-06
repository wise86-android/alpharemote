package org.staacks.alpharemote

import android.bluetooth.le.ScanResult
import android.companion.CompanionDeviceManager
import android.content.Intent
import android.content.IntentSender
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.scene.DialogSceneStrategy
import androidx.navigation3.ui.NavDisplay
import org.staacks.alpharemote.camera.CameraAction
import org.staacks.alpharemote.service.AlphaRemoteService
import org.staacks.alpharemote.ui.about.AboutScreen
import org.staacks.alpharemote.ui.camera.CameraScreen
import org.staacks.alpharemote.ui.camera.CameraViewModel
import org.staacks.alpharemote.ui.settings.CompanionDeviceHelper
import org.staacks.alpharemote.ui.settings.SettingScreen
import org.staacks.alpharemote.ui.settings.SettingsViewModel
import org.staacks.alpharemote.ui.theme.BluetoothRemoteForSonyCamerasTheme
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.core.net.toUri
import org.staacks.alpharemote.data.SettingsStore
import org.staacks.alpharemote.ui.camera.RemoteButton
import org.staacks.alpharemote.ui.settings.CameraActionPickerContent
import org.staacks.alpharemote.ui.settings.CameraActionPicker
import java.io.Serializable

class MainActivity : ComponentActivity() {

    companion object {
        const val TAG: String = "alpharemote"
    }

    private val onDeviceFoundLauncher = registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { activityResult ->
        if (activityResult.resultCode == RESULT_OK) {
            val scanResult: ScanResult? = extractScanResult(activityResult.data)
            scanResult?.let { result ->
                CompanionDeviceHelper.startObservingDevicePresence(this, result.device)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {
            BluetoothRemoteForSonyCamerasTheme {
                MainScreen(
                    onPairRequested = { chooserLauncher ->
                        onDeviceFoundLauncher.launch(IntentSenderRequest.Builder(chooserLauncher).build())
                    },
                    onOpenUrl = { url ->
                        startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
                    },
                    onSendCameraAction = ::sendCameraActionToService,
                    onStartAdvancedSequence = ::startAdvancedSequence
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        // Note: The Composable will handle the intent change via LaunchedEffect
    }

    private fun sendCameraActionToService(cameraAction: CameraAction, event: Int?) {
        val intent = Intent(this, AlphaRemoteService::class.java).apply {
            action = AlphaRemoteService.BUTTON_INTENT_ACTION
            putExtra(AlphaRemoteService.BUTTON_INTENT_CAMERA_ACTION_EXTRA, cameraAction as Serializable)
            event?.let {
                if (event == android.view.MotionEvent.ACTION_DOWN)
                    putExtra(AlphaRemoteService.BUTTON_INTENT_CAMERA_ACTION_UP_EXTRA, false)
                else
                    putExtra(AlphaRemoteService.BUTTON_INTENT_CAMERA_ACTION_DOWN_EXTRA, false)
            }
        }
        startService(intent)
    }

    private fun startAdvancedSequence(bulbDuration: Float, intervalCount: Int, intervalDuration: Float) {
        val intent = Intent(this, AlphaRemoteService::class.java).apply {
            action = AlphaRemoteService.ADVANCED_SEQUENCE_INTENT_ACTION
            putExtra(AlphaRemoteService.ADVANCED_SEQUENCE_INTENT_BULB_DURATION_EXTRA, bulbDuration)
            putExtra(AlphaRemoteService.ADVANCED_SEQUENCE_INTENT_INTERVAL_COUNT_EXTRA, intervalCount)
            putExtra(AlphaRemoteService.ADVANCED_SEQUENCE_INTENT_INTERVAL_DURATION_EXTRA, intervalDuration)
        }
        startService(intent)
    }

    @Suppress("DEPRECATION")
    private fun extractScanResult(intent: Intent?): ScanResult? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent?.getParcelableExtra(CompanionDeviceManager.EXTRA_DEVICE, ScanResult::class.java)
        } else {
            intent?.getParcelableExtra(CompanionDeviceManager.EXTRA_DEVICE)
        }
    }
}

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
            val settingsStore = SettingsStore(androidx.compose.ui.platform.LocalContext.current)
            
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
                                RemoteButton.SHUTTER -> org.staacks.alpharemote.camera.CameraActionPreset.SHUTTER
                                RemoteButton.SHUTTER_HALF -> org.staacks.alpharemote.camera.CameraActionPreset.SHUTTER_HALF
                                RemoteButton.SELFTIMER_3S -> org.staacks.alpharemote.camera.CameraActionPreset.TRIGGER_ONCE
                                RemoteButton.RECORD -> org.staacks.alpharemote.camera.CameraActionPreset.RECORD
                                RemoteButton.C1 -> org.staacks.alpharemote.camera.CameraActionPreset.C1
                                RemoteButton.AF_ON -> org.staacks.alpharemote.camera.CameraActionPreset.AF_ON
                                RemoteButton.ZOOM_IN -> org.staacks.alpharemote.camera.CameraActionPreset.ZOOM_IN
                                RemoteButton.ZOOM_OUT -> org.staacks.alpharemote.camera.CameraActionPreset.ZOOM_OUT
                                RemoteButton.FOCUS_FAR -> org.staacks.alpharemote.camera.CameraActionPreset.FOCUS_FAR
                                RemoteButton.FOCUS_NEAR -> org.staacks.alpharemote.camera.CameraActionPreset.FOCUS_NEAR
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
            val context = androidx.compose.ui.platform.LocalContext.current
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
                    navigator.navigate(AlphaRemoteNavKey.CameraActionPicker(-1, CameraAction(false, null, null, null, org.staacks.alpharemote.camera.CameraActionPreset.STOP), false))
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
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(28.dp),
                        color = androidx.compose.material3.MaterialTheme.colorScheme.surface,
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
