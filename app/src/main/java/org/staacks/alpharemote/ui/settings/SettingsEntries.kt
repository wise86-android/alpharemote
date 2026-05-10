package org.staacks.alpharemote.ui.settings

import android.app.Activity.RESULT_OK
import android.companion.AssociationInfo
import android.companion.CompanionDeviceManager
import android.content.IntentSender
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.scene.DialogSceneStrategy
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import org.staacks.alpharemote.R
import org.staacks.alpharemote.camera.CameraAction
import org.staacks.alpharemote.camera.CameraActionPreset
import org.staacks.alpharemote.service.AlphaRemoteService
import org.staacks.alpharemote.ui.AlphaRemoteNavKey
import org.staacks.alpharemote.ui.Navigator
import org.staacks.alpharemote.utils.openUrl
import org.staacks.alpharemote.utils.rememberBlePermissionState

@OptIn(ExperimentalPermissionsApi::class)
fun EntryProviderScope<AlphaRemoteNavKey>.settingsEntries(
    settingsViewModel: SettingsViewModel,
    navigator: Navigator
) {
    entry<AlphaRemoteNavKey.Settings> {
        val context = LocalContext.current
        var showBluetoothDialog by remember { mutableStateOf(false) }

        val bluetoothPermissionState = rememberBlePermissionState()

        if (showBluetoothDialog) {
            AlertDialog(
                onDismissRequest = { showBluetoothDialog = false },
                title = { Text(stringResource(R.string.settings_bluetooth_required_title)) },
                text = { Text(stringResource(R.string.settings_bluetooth_required_message)) },
                confirmButton = {
                    TextButton(onClick = {
                        showBluetoothDialog = false
                        settingsViewModel.openBluetoothSettings()
                    }) {
                        Text(stringResource(R.string.settings_open_settings))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showBluetoothDialog = false }) {
                        Text(stringResource(android.R.string.cancel))
                    }
                }
            )
        }

        LaunchedEffect(settingsViewModel) {
            settingsViewModel.uiAction.collect { action ->
                when (action) {
                    SettingsViewModel.SettingsUIAction.SHOW_BLUETOOTH_REQUIRED_DIALOG -> {
                        showBluetoothDialog = true
                    }
                    SettingsViewModel.SettingsUIAction.OPEN_BLUETOOTH_SETTINGS -> {
                        context.startActivity(android.content.Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS))
                    }
                    SettingsViewModel.SettingsUIAction.REQUEST_BLUETOOTH_PERMISSION -> {
                        bluetoothPermissionState.launchPermissionRequest()
                    }
                    else -> {}
                }
            }
        }

        val pairLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.StartIntentSenderForResult()
        ) { result ->
            if (result.resultCode == RESULT_OK) {
                val associationInfo = result.data?.getParcelableExtra(
                    CompanionDeviceManager.EXTRA_ASSOCIATION,
                    AssociationInfo::class.java
                )
                associationInfo?.associatedDevice?.bleDevice?.let { bleDevice ->
                    CompanionDeviceHelper.startObservingDevicePresence(context, bleDevice.device)
                }
            }
        }

        SettingScreen(
            settingsViewModel = settingsViewModel,
            onPairRequested = {
                CompanionDeviceHelper.pairCompanionDevice(context, object : CompanionDeviceManager.Callback() {
                    override fun onAssociationPending(intentSender: IntentSender) {
                        pairLauncher.launch(IntentSenderRequest.Builder(intentSender).build())
                    }

                    override fun onAssociationCreated(associationInfo: AssociationInfo) {
                        associationInfo.associatedDevice?.bleDevice?.let { bleDevice ->
                            CompanionDeviceHelper.startObservingDevicePresence(context, bleDevice.device)
                        }
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
                navigator.navigate(
                    AlphaRemoteNavKey.CameraActionPicker(
                        -1,
                        CameraAction(false, null, null, null, CameraActionPreset.STOP),
                        false
                    )
                )
            },
            onHelpConnectionRequested = {
                navigator.navigate(
                    AlphaRemoteNavKey.Help(
                        R.string.help_settings_connection_troubleshooting_title,
                        R.string.help_settings_connection_troubleshooting_text
                    )
                )
            },
            onHelpCustomButtonsRequested = {
                navigator.navigate(
                    AlphaRemoteNavKey.Help(
                        R.string.help_settings_custom_buttons_title,
                        R.string.help_settings_custom_buttons_text
                    )
                )
            },
            onEditCustomButton = { index, action ->
                navigator.navigate(AlphaRemoteNavKey.CameraActionPicker(index, action, true))
            },
            onOpenUrl = { url -> context.openUrl(url) }
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
