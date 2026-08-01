package org.staacks.alpharemote.feature.wificamera.ui

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Nfc
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.staacks.alpharemote.feature.wificamera.domain.CameraMode
import org.staacks.alpharemote.feature.wificamera.domain.FailureReason
import org.staacks.alpharemote.feature.wificamera.domain.WifiCameraConnection
import org.staacks.alpharemote.feature.wificamera.domain.WifiCredentials
import org.staacks.alpharemote.feature.wificamera.ui.theme.CameraColors
import org.staacks.alpharemote.feature.wificamera.ui.theme.CameraType

/**
 * Entry point for the Wi-Fi camera tab.
 *
 * Shows the camera back once there is a camera to show, and otherwise explains what is missing.
 * The mockup assumes a live connection, so everything before that has to live somewhere — this
 * is that somewhere.
 */
@Composable
fun WifiCameraScreen(viewModel: WifiCameraViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(viewModel) {
        viewModel.messages.collect { snackbarHostState.showSnackbar(it) }
    }

    Box(Modifier.fillMaxSize().background(CameraColors.Background)) {
        val connection = state.connection
        if (connection is WifiCameraConnection.Connected &&
            connection.mode == CameraMode.CONTENTS_TRANSFER
        ) {
            // In this mode the camera offers no live view, settings or shutter — only the images
            // the user picked on the body.
            val download by viewModel.download.collectAsStateWithLifecycle()
            DownloadScreen(
                state = download,
                onStart = viewModel::startDownload,
                onCancel = viewModel::cancelDownload
            )
        } else if (connection is WifiCameraConnection.Connected) {
            val shutter by viewModel.shutter.collectAsStateWithLifecycle()
            CameraControlScreen(
                camera = state.camera,
                cameraName = connection.camera.friendlyName
                    .ifBlank { connection.camera.modelName },
                onSelect = viewModel::select,
                onFocus = viewModel::focus,
                onShoot = viewModel::shoot,
                onCancelFocus = viewModel::cancelFocus,
                shutter = shutter,
                liveView = {
                    // Collected here rather than beside `uiState` so that a frame arriving at
                    // 30 fps only recomposes the viewfinder, not the readouts around it.
                    val liveView by viewModel.liveView.collectAsStateWithLifecycle()
                    LiveViewSurface(liveView)
                }
            )
        } else {
            val knownCamera by viewModel.knownCamera.collectAsStateWithLifecycle()
            ConnectionPanel(
                connection = connection,
                knownCamera = knownCamera,
                onConnect = viewModel::connect,
                onForget = viewModel::forgetCamera,
                modifier = Modifier.align(Alignment.Center)
            )
        }

        SnackbarHost(snackbarHostState, Modifier.align(Alignment.BottomCenter))
    }
}

@Composable
private fun ConnectionPanel(
    connection: WifiCameraConnection,
    knownCamera: WifiCredentials?,
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
            text = if (knownCamera == null && !busy) {
                "Touch your camera to the phone"
            } else {
                connection.headline()
            },
            style = CameraType.hudMedium,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(8.dp))
        val detail = (connection as? WifiCameraConnection.Failed)?.detail
            ?: if (knownCamera == null) {
                "Enable NFC, then hold the phone against the N-Mark on the camera. It will " +
                    "hand over its Wi-Fi details and switch its own Wi-Fi on."
            } else {
                knownCamera.ssid
            }
        Text(text = detail, style = CameraType.hudSmallDim, textAlign = TextAlign.Center)

        Spacer(Modifier.height(24.dp))
        when {
            busy -> TextButton(onClick = onConnect, enabled = false) { Text("Connecting…") }

            knownCamera == null -> Unit

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
                    Text(if (connection is WifiCameraConnection.Failed) "Try again" else "Connect")
                }
                TextButton(onClick = onForget) {
                    Text("Forget this camera", style = CameraType.hudSmallDim)
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
