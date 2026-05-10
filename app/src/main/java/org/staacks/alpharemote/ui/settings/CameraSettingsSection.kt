package org.staacks.alpharemote.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.staacks.alpharemote.R
import org.staacks.alpharemote.ui.settings.SettingsViewModel.SettingsUICameraState
import org.staacks.alpharemote.ui.settings.SettingsViewModel.SettingsUIState
import org.staacks.alpharemote.ui.theme.BluetoothRemoteForSonyCamerasTheme

@Composable
fun CameraSettingsSection(
    state: SettingsUIState,
    onPairClick: () -> Unit,
    onUnpairClick: () -> Unit,
    onHelpClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val cameraName = state.cameraName ?: stringResource(R.string.settings_camera_unknown_name)

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = stringResource(R.string.settings_camera),
            style = MaterialTheme.typography.headlineSmall,
        )

        if (!state.bluetoothEnabled) {
            Text(
                text = stringResource(R.string.settings_bluetooth_disabled),
                color = MaterialTheme.colorScheme.error,
            )
        }

        when (state.cameraState) {
            SettingsUICameraState.OFFLINE -> {
                Text(
                    text = stringResource(R.string.settings_camera_offline, cameraName),
                )
            }
            SettingsUICameraState.CONNECTED -> {
                Text(
                    text = stringResource(R.string.settings_camera_connected, cameraName),
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            SettingsUICameraState.ERROR -> {
                state.cameraError?.let {
                    Text(
                        text = stringResource(R.string.settings_camera_error, it),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
            SettingsUICameraState.NOT_ASSOCIATED -> {
                Text(
                    text = stringResource(R.string.settings_camera_not_associated),
                )
            }
            SettingsUICameraState.NOT_BONDED -> {
                Text(
                    text = stringResource(R.string.settings_camera_not_bonded),
                    color = MaterialTheme.colorScheme.error,
                )
            }
            SettingsUICameraState.REMOTE_DISABLED -> {
                Text(
                    text = stringResource(R.string.settings_camera_remote_disabled),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (state.cameraState == SettingsUICameraState.NOT_ASSOCIATED) {
                TextButton (
                    onClick = onPairClick,
                ) {
                    Text(text = stringResource(R.string.settings_camera_add))
                }
            } else {
                TextButton(onClick = onUnpairClick) {
                    Text(text = stringResource(R.string.settings_camera_remove))
                }
            }

            TextButton(onClick = onHelpClick) {
                Text(text = stringResource(R.string.help))
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun CameraSettingsSectionNotAssociatedPreview() {
    BluetoothRemoteForSonyCamerasTheme {
        CameraSettingsSection(
            state = SettingsUIState(
                cameraState = SettingsUICameraState.NOT_ASSOCIATED,
                cameraError = null,
                cameraName = null,
                bluetoothEnabled = true,
                locationServiceEnabled = true,
            ),
            onPairClick = {},
            onUnpairClick = {},
            onHelpClick = {},
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun CameraSettingsSectionConnectedPreview() {
    BluetoothRemoteForSonyCamerasTheme {
        CameraSettingsSection(
            state = SettingsUIState(
                cameraState = SettingsUICameraState.CONNECTED,
                cameraError = null,
                cameraName = "Alpha 1",
                bluetoothEnabled = true,
                locationServiceEnabled = true,
            ),
            onPairClick = {},
            onUnpairClick = {},
            onHelpClick = {},
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
