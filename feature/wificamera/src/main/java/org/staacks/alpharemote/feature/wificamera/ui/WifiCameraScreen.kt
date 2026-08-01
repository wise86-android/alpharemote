package org.staacks.alpharemote.feature.wificamera.ui

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.staacks.alpharemote.feature.wificamera.domain.CameraOption
import org.staacks.alpharemote.feature.wificamera.domain.CameraSetting
import org.staacks.alpharemote.feature.wificamera.domain.FailureReason
import org.staacks.alpharemote.feature.wificamera.domain.WifiCameraConnection

/**
 * A deliberately plain view of the camera session.
 *
 * This exists to prove the pipeline: connect, then watch values change here as they are changed
 * on the camera body. The designed screen — live view behind a HUD, drum pickers — replaces it
 * later and will read the same [WifiCameraViewModel.UiState].
 */
@Composable
fun WifiCameraScreen(viewModel: WifiCameraViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) viewModel.connect() }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        ConnectionCard(state.connection)

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                // Asking every time is harmless once granted and keeps the flow in one place.
                onClick = { permissionLauncher.launch(Manifest.permission.NEARBY_WIFI_DEVICES) },
                enabled = state.connection !is WifiCameraConnection.Connected
            ) {
                Text("Connect")
            }
            OutlinedButton(onClick = viewModel::disconnect) {
                Text("Disconnect")
            }
        }

        if (state.settings.isEmpty()) {
            Text(
                text = "No settings reported yet.",
                style = MaterialTheme.typography.bodyMedium
            )
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(state.settings, key = { it.id.name }) { setting ->
                    SettingCard(
                        setting = setting,
                        onSelect = { option -> viewModel.select(setting.id, option) }
                    )
                }
            }
        }
    }
}

@Composable
private fun ConnectionCard(connection: WifiCameraConnection) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Text(
                text = connection.describe(),
                style = MaterialTheme.typography.titleMedium
            )
            (connection as? WifiCameraConnection.Failed)?.let { failed ->
                failed.detail?.let {
                    Text(text = it, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SettingCard(
    setting: CameraSetting,
    onSelect: (CameraOption) -> Unit
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(setting.id.label, style = MaterialTheme.typography.labelLarge)
                Text(
                    text = setting.current?.label ?: "--",
                    style = MaterialTheme.typography.titleMedium
                )
            }

            // Only settings with a setter on this body can be changed from here; the rest are
            // read-only mirrors of what the camera reports.
            if (setting.writable && setting.available.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    setting.available.forEach { option ->
                        FilterChip(
                            selected = option.label == setting.current?.label,
                            onClick = { onSelect(option) },
                            label = { Text(option.label) }
                        )
                    }
                }
            }
        }
    }
}

private fun WifiCameraConnection.describe(): String = when (this) {
    WifiCameraConnection.Idle -> "Not connected"
    WifiCameraConnection.JoiningWifi -> "Joining the camera's Wi-Fi…"
    WifiCameraConnection.Discovering -> "Looking for the camera…"
    WifiCameraConnection.Handshaking -> "Connecting…"
    is WifiCameraConnection.Connected -> camera.friendlyName.ifBlank { camera.modelName }
    is WifiCameraConnection.Failed -> when (reason) {
        FailureReason.MISSING_PERMISSION -> "Nearby-devices permission is required"
        FailureReason.WIFI_JOIN_FAILED -> "Could not join the camera's Wi-Fi"
        FailureReason.CAMERA_NOT_FOUND -> "No camera answered"
        FailureReason.CLEARTEXT_BLOCKED -> "The camera's address is blocked by app policy"
        FailureReason.WRONG_CAMERA_MODE -> "Camera is not in remote shooting mode"
        FailureReason.UNSUPPORTED_PROTOCOL -> "This camera speaks PTP/IP, which is not supported"
        // Covers both "could not read the camera" and "lost it mid-session"; the detail below
        // says which.
        FailureReason.NETWORK_ERROR -> "Could not talk to the camera"
    }
}
