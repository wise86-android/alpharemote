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
import org.staacks.alpharemote.feature.wificamera.domain.FailureReason
import org.staacks.alpharemote.feature.wificamera.domain.WifiCameraConnection
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
        if (connection is WifiCameraConnection.Connected) {
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
            ConnectionPanel(
                connection = connection,
                onConnect = viewModel::connect,
                modifier = Modifier.align(Alignment.Center)
            )
        }

        SnackbarHost(snackbarHostState, Modifier.align(Alignment.BottomCenter))
    }
}

@Composable
private fun ConnectionPanel(
    connection: WifiCameraConnection,
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

    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (busy) {
            CircularProgressIndicator(
                color = CameraColors.AccentAmber,
                modifier = Modifier.size(32.dp)
            )
        } else {
            Icon(
                imageVector = Icons.Filled.WifiOff,
                contentDescription = null,
                tint = CameraColors.TextTertiary,
                modifier = Modifier.size(40.dp)
            )
        }

        Spacer(Modifier.height(16.dp))
        Text(
            text = connection.headline(),
            style = CameraType.hudMedium,
            textAlign = TextAlign.Center
        )

        (connection as? WifiCameraConnection.Failed)?.detail?.let { detail ->
            Spacer(Modifier.height(8.dp))
            Text(
                text = detail,
                style = CameraType.hudSmallDim,
                textAlign = TextAlign.Center
            )
        }

        Spacer(Modifier.height(24.dp))
        if (busy) {
            TextButton(onClick = onConnect, enabled = false) { Text("Connecting…") }
        } else {
            Button(
                onClick = { permissionLauncher.launch(Manifest.permission.NEARBY_WIFI_DEVICES) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = CameraColors.AccentAmber,
                    contentColor = CameraColors.Background
                )
            ) {
                Text(if (connection is WifiCameraConnection.Failed) "Try again" else "Connect")
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
