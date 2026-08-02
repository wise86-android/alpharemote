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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.staacks.alpharemote.feature.wificamera.data.ble.WifiHandoverAvailability
import org.staacks.alpharemote.feature.wificamera.domain.FailureReason
import org.staacks.alpharemote.feature.wificamera.domain.WifiCameraConnection
import org.staacks.alpharemote.feature.wificamera.domain.WifiCredentials
import org.staacks.alpharemote.feature.wificamera.ui.theme.CameraColors
import org.staacks.alpharemote.feature.wificamera.ui.theme.CameraType

/**
 * What [WifiCameraScreenContent] shows in place of the camera back while there is no camera.
 *
 * [bleAvailability] is [WifiHandoverAvailability.READY] when a paired camera is reachable over
 * BLE right now — in that case [onConnect] will turn its Wi-Fi on itself, so the prompt is to ask
 * for that rather than to tap. It takes priority over [knownCamera] when both are true: BLE gives
 * fresh credentials without needing anything cached from an earlier tap.
 */
@Composable
internal fun ConnectionPanel(
    connection: WifiCameraConnection,
    knownCamera: WifiCredentials?,
    bleAvailability: WifiHandoverAvailability,
    onConnect: () -> Unit,
    onForget: () -> Unit,
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
    // Something to connect to, one way or another, once busy is ruled out.
    val canConnect = bleReady || knownCamera != null

    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        when {
            busy -> CircularProgressIndicator(
                color = CameraColors.AccentAmber,
                modifier = Modifier.size(32.dp)
            )

            bleReady -> Icon(
                imageVector = Icons.Filled.Bluetooth,
                contentDescription = null,
                tint = CameraColors.AccentTeal,
                modifier = Modifier.size(48.dp)
            )

            // Nothing has been tapped yet, so the prompt is to tap rather than to connect.
            knownCamera == null -> Icon(
                imageVector = Icons.Filled.Nfc,
                contentDescription = null,
                tint = CameraColors.AccentTeal,
                modifier = Modifier.size(48.dp)
            )

            else -> Icon(
                imageVector = Icons.Filled.WifiOff,
                contentDescription = null,
                tint = CameraColors.TextTertiary,
                modifier = Modifier.size(40.dp)
            )
        }

        Spacer(Modifier.height(16.dp))
        Text(
            text = when {
                busy -> connection.headline()
                bleReady -> "Camera detected nearby"
                knownCamera == null -> "Touch your camera to the phone"
                else -> connection.headline()
            },
            style = CameraType.hudMedium,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(8.dp))
        val detail = (connection as? WifiCameraConnection.Failed)?.detail
            ?: when {
                bleReady -> "Connected over Bluetooth. Turn on its Wi-Fi to finish connecting — " +
                    "no tap needed."
                knownCamera == null ->
                    "Enable NFC, then hold the phone against the N-Mark on the camera. It " +
                        "will hand over its Wi-Fi details and switch its own Wi-Fi on."
                else -> knownCamera.ssid
            }
        Text(text = detail, style = CameraType.hudSmallDim, textAlign = TextAlign.Center)

        Spacer(Modifier.height(24.dp))
        when {
            busy -> TextButton(onClick = onConnect, enabled = false) { Text("Connecting…") }

            !canConnect -> Unit

            else -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Button(
                    onClick = {
                        permissionLauncher.launch(Manifest.permission.NEARBY_WIFI_DEVICES)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = CameraColors.AccentAmber,
                        contentColor = CameraColors.Background
                    )
                ) {
                    Text(
                        when {
                            connection is WifiCameraConnection.Failed -> "Try again"
                            bleReady -> "Turn on camera Wi-Fi"
                            else -> "Connect"
                        }
                    )
                }
                if (knownCamera != null) {
                    TextButton(onClick = onForget) {
                        Text("Forget this camera", style = CameraType.hudSmallDim)
                    }
                }
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

private val previewCredentials = WifiCredentials(ssid = "DIRECT-abcd:ILCE-6600", password = "password")

@Preview(name = "Nothing tapped yet", showBackground = true, backgroundColor = 0xFF0D0D0F)
@Composable
private fun ConnectionPanelUnknownPreview() {
    ConnectionPanel(
        connection = WifiCameraConnection.Idle,
        knownCamera = null,
        bleAvailability = WifiHandoverAvailability.UNAVAILABLE,
        onConnect = {},
        onForget = {}
    )
}

/** A paired camera is BLE-connected — takes priority over any stored credentials. */
@Preview(name = "Camera detected over BLE", showBackground = true, backgroundColor = 0xFF0D0D0F)
@Composable
private fun ConnectionPanelBleReadyPreview() {
    ConnectionPanel(
        connection = WifiCameraConnection.Idle,
        knownCamera = null,
        bleAvailability = WifiHandoverAvailability.READY,
        onConnect = {},
        onForget = {}
    )
}

@Preview(name = "Joining", showBackground = true, backgroundColor = 0xFF0D0D0F)
@Composable
private fun ConnectionPanelBusyPreview() {
    ConnectionPanel(
        connection = WifiCameraConnection.JoiningWifi,
        knownCamera = previewCredentials,
        bleAvailability = WifiHandoverAvailability.UNAVAILABLE,
        onConnect = {},
        onForget = {}
    )
}

@Preview(name = "Known camera, disconnected", showBackground = true, backgroundColor = 0xFF0D0D0F)
@Composable
private fun ConnectionPanelKnownPreview() {
    ConnectionPanel(
        connection = WifiCameraConnection.Idle,
        knownCamera = previewCredentials,
        bleAvailability = WifiHandoverAvailability.UNAVAILABLE,
        onConnect = {},
        onForget = {}
    )
}

@Preview(name = "Failed", showBackground = true, backgroundColor = 0xFF0D0D0F)
@Composable
private fun ConnectionPanelFailedPreview() {
    ConnectionPanel(
        connection = WifiCameraConnection.Failed(
            FailureReason.CAMERA_NOT_FOUND,
            "No camera answered after 20 seconds."
        ),
        knownCamera = previewCredentials,
        bleAvailability = WifiHandoverAvailability.UNAVAILABLE,
        onConnect = {},
        onForget = {}
    )
}
