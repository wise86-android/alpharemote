package org.staacks.alpharemote.feature.wificamera.ui.connection

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Nfc
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.staacks.alpharemote.core.ui.theme.BluetoothRemoteForSonyCamerasTheme
import org.staacks.alpharemote.feature.wificamera.data.ble.WifiHandoverAvailability
import org.staacks.alpharemote.feature.wificamera.domain.FailureReason
import org.staacks.alpharemote.feature.wificamera.domain.WifiCameraConnection

/**
 * What [WifiCameraScreenContent] shows in place of the camera back while there is no camera.
 *
 * [bleAvailability] is [WifiHandoverAvailability.READY] when a paired camera is reachable over
 * BLE right now — in that case [onConnect] turns its Wi-Fi on itself, so the prompt is to ask for
 * that. Otherwise the only way in is a fresh NFC tap: there is no cached credential to fall back
 * to, so the panel always either offers the live BLE handover or asks for a tap, never a "connect
 * to what you tapped last time" button.
 */
@Composable
internal fun ConnectionPanel(
    connection: WifiCameraConnection,
    bleAvailability: WifiHandoverAvailability,
    onConnect: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Asking each time is harmless once granted, and keeps permission and connect in one gesture.
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) onConnect() }

    val busy = connection is WifiCameraConnection.JoiningWifi ||
        connection is WifiCameraConnection.Discovering ||
        connection is WifiCameraConnection.Handshaking
    val bleReady = bleAvailability == WifiHandoverAvailability.READY

    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        when {
            busy -> CircularProgressIndicator(
                color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.size(32.dp)
            )

            bleReady -> Icon(
                imageVector = Icons.Filled.Bluetooth,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(48.dp)
            )

            else -> Icon(
                imageVector = Icons.Filled.Nfc,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(48.dp)
            )
        }

        Spacer(Modifier.height(16.dp))
        Text(
            text = when {
                busy -> connection.headline()
                bleReady -> "Camera detected nearby"
                else -> "Touch your camera to the phone"
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(8.dp))
        val detail = (connection as? WifiCameraConnection.Failed)?.detail
            ?: when {
                bleReady -> "Connected over Bluetooth. Turn on its Wi-Fi to finish connecting — " +
                    "no tap needed."
                else ->
                    "Enable NFC, then hold the phone against the N-Mark on the camera. It " +
                        "will hand over its Wi-Fi details and switch its own Wi-Fi on."
            }
        Text(
            text = detail,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(24.dp))
        when {
            busy -> TextButton(onClick = onConnect, enabled = false) { Text("Connecting…") }

            !bleReady -> Unit

            else -> Button(
                onClick = {
                    permissionLauncher.launch(Manifest.permission.NEARBY_WIFI_DEVICES)
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondary,
                    contentColor = MaterialTheme.colorScheme.onSecondary
                )
            ) {
                Text(if (connection is WifiCameraConnection.Failed) "Try again" else "Turn on camera Wi-Fi")
            }
        }
    }
}

private fun WifiCameraConnection.headline(): String = when (this) {
    WifiCameraConnection.Idle -> "Not connected"
    WifiCameraConnection.JoiningWifi -> "Joining the camera's Wi-Fi…"
    WifiCameraConnection.Discovering -> "Looking for the camera…"
    WifiCameraConnection.Handshaking -> "Connecting…"
    is WifiCameraConnection.Connected -> camera.friendlyName
    is WifiCameraConnection.Failed -> when (reason) {
        FailureReason.MISSING_PERMISSION -> "Nearby-devices permission is required"
        FailureReason.WIFI_JOIN_FAILED -> "Could not join the camera's Wi-Fi"
        FailureReason.CAMERA_NOT_FOUND -> "No camera answered"
        FailureReason.CLEARTEXT_BLOCKED -> "The camera's address is blocked by app policy"
        FailureReason.WRONG_CAMERA_MODE -> "Camera is not in remote shooting mode"
        FailureReason.UNSUPPORTED_PROTOCOL -> "This camera speaks PTP/IP, which is not supported"
        // Covers both "could not read the camera" and "lost it mid-session"; the detail says which.
        FailureReason.NETWORK_ERROR -> "Could not talk to the camera"
    }
}

@Preview(name = "Nothing tapped yet", showBackground = true)
@Composable
private fun ConnectionPanelUnknownPreview() {
    BluetoothRemoteForSonyCamerasTheme {
        ConnectionPanel(
            connection = WifiCameraConnection.Idle,
            bleAvailability = WifiHandoverAvailability.UNAVAILABLE,
            onConnect = {}
        )
    }
}

/** A paired camera is BLE-connected — the only state that offers a "Connect" button at all. */
@Preview(name = "Camera detected over BLE", showBackground = true)
@Composable
private fun ConnectionPanelBleReadyPreview() {
    BluetoothRemoteForSonyCamerasTheme {
        ConnectionPanel(
            connection = WifiCameraConnection.Idle,
            bleAvailability = WifiHandoverAvailability.READY,
            onConnect = {}
        )
    }
}

@Preview(name = "Joining", showBackground = true)
@Composable
private fun ConnectionPanelBusyPreview() {
    BluetoothRemoteForSonyCamerasTheme {
        ConnectionPanel(
            connection = WifiCameraConnection.JoiningWifi,
            bleAvailability = WifiHandoverAvailability.UNAVAILABLE,
            onConnect = {}
        )
    }
}

/** No cached credentials to retry with — the only way forward is another tap. */
@Preview(name = "Failed, no BLE", showBackground = true)
@Composable
private fun ConnectionPanelFailedPreview() {
    BluetoothRemoteForSonyCamerasTheme {
        ConnectionPanel(
            connection = WifiCameraConnection.Failed(
                FailureReason.CAMERA_NOT_FOUND,
                "No camera answered after 20 seconds."
            ),
            bleAvailability = WifiHandoverAvailability.UNAVAILABLE,
            onConnect = {}
        )
    }
}

@Preview(name = "Failed, BLE ready", showBackground = true)
@Composable
private fun ConnectionPanelFailedBleReadyPreview() {
    BluetoothRemoteForSonyCamerasTheme {
        ConnectionPanel(
            connection = WifiCameraConnection.Failed(
                FailureReason.WIFI_JOIN_FAILED,
                "Could not join the camera's Wi-Fi."
            ),
            bleAvailability = WifiHandoverAvailability.READY,
            onConnect = {}
        )
    }
}
