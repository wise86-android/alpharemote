package org.staacks.alpharemote.feature.ble.ui.camera
import org.staacks.alpharemote.feature.ble.R

import android.content.res.Configuration
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.staacks.alpharemote.core.ui.theme.BluetoothRemoteForSonyCamerasTheme

/**
 * Shown in place of the remote whenever there is no button-press control available: either no
 * camera is connected at all, or [remoteDisabled] — the camera is bonded and reachable over
 * Bluetooth (position sync may be running) but its own "Bluetooth remote control" setting is
 * off, so no button press would do anything. In that case this advertises the Wi-Fi remote
 * instead, which still uses the Bluetooth connection to hand over the camera's Wi-Fi credentials.
 */
@Composable
fun DisconnectedCameraView(
    remoteDisabled: Boolean = false,
    onGotoSettings: () -> Unit,
    onGotoWifiCamera: () -> Unit = {},
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(30.dp)
    ) {
        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(
                    if (remoteDisabled) R.string.camera_remote_disabled else R.string.camera_not_connected
                ),
                modifier = Modifier.width(400.dp),
            )
            Spacer(modifier = Modifier.height(30.dp))
            if (remoteDisabled) {
                TextButton(onClick = onGotoWifiCamera) {
                    Icon(Icons.Default.Wifi, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = stringResource(R.string.camera_remote_disabled_wifi_button))
                }
            } else {
                TextButton(onClick = onGotoSettings) {
                    Icon(Icons.Default.Settings, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = stringResource(R.string.title_settings))
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun DisconnectedCameraViewPreview() {
    BluetoothRemoteForSonyCamerasTheme {
        Surface {
            DisconnectedCameraView(onGotoSettings = {})
        }
    }
}
@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun DisconnectedCameraViewDarkPreview() {
    BluetoothRemoteForSonyCamerasTheme {
        Surface {
            DisconnectedCameraView(onGotoSettings = {})
        }
    }
}

@Preview(name = "Remote disabled — syncing position", showBackground = true)
@Composable
private fun DisconnectedCameraViewRemoteDisabledPreview() {
    BluetoothRemoteForSonyCamerasTheme {
        Surface {
            DisconnectedCameraView(remoteDisabled = true, onGotoSettings = {}, onGotoWifiCamera = {})
        }
    }
}

