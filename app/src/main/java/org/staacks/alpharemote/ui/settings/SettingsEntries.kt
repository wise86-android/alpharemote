package org.staacks.alpharemote.ui.settings

import android.companion.CompanionDeviceManager
import android.content.IntentSender
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.scene.DialogSceneStrategy
import org.staacks.alpharemote.R
import org.staacks.alpharemote.camera.CameraAction
import org.staacks.alpharemote.camera.CameraActionPreset
import org.staacks.alpharemote.service.AlphaRemoteService
import org.staacks.alpharemote.ui.AlphaRemoteNavKey
import org.staacks.alpharemote.ui.Navigator

fun EntryProviderScope<AlphaRemoteNavKey>.settingsEntries(
    settingsViewModel: SettingsViewModel,
    navigator: Navigator,
    onPairRequested: (IntentSender) -> Unit,
    onOpenUrl: (String) -> Unit
) {
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
            onOpenUrl = onOpenUrl
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
