package org.staacks.alpharemote.feature.wificamera.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.staacks.alpharemote.feature.wificamera.data.ble.WifiHandoverAvailability
import org.staacks.alpharemote.feature.wificamera.domain.CameraIdentity
import org.staacks.alpharemote.feature.wificamera.domain.CameraMode
import org.staacks.alpharemote.feature.wificamera.domain.CameraOption
import org.staacks.alpharemote.feature.wificamera.domain.CameraSettingId
import org.staacks.alpharemote.feature.wificamera.domain.CameraSnapshot
import org.staacks.alpharemote.feature.wificamera.domain.WifiCameraConnection
import org.staacks.alpharemote.feature.wificamera.ui.cameracontrol.CameraControlScreen
import org.staacks.alpharemote.feature.wificamera.ui.cameracontrol.ShutterState
import org.staacks.alpharemote.feature.wificamera.ui.connection.ConnectionPanel
import org.staacks.alpharemote.feature.wificamera.ui.download.DownloadScreen
import org.staacks.alpharemote.feature.wificamera.ui.download.DownloadUiState
import org.staacks.alpharemote.feature.wificamera.ui.liveview.LiveViewState
import org.staacks.alpharemote.feature.wificamera.ui.liveview.LiveViewSurface
import org.staacks.alpharemote.core.ui.theme.BluetoothRemoteForSonyCamerasTheme

/**
 * Entry point for the Wi-Fi camera tab.
 *
 * A thin shell: it collects the view model's flows and a one-shot message stream, and delegates
 * everything about *what* to show to [WifiCameraScreenContent]. That split exists so the content
 * can be previewed — an `AndroidViewModel` cannot be constructed in a `@Preview` without touching
 * real system services (DataStore, connectivity), so this composable itself has none.
 */
@Composable
fun WifiCameraScreen(viewModel: WifiCameraViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val shutter by viewModel.shutter.collectAsStateWithLifecycle()
    val download by viewModel.download.collectAsStateWithLifecycle()
    val bleAvailability by viewModel.bleAvailability.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(viewModel) {
        viewModel.messages.collect { snackbarHostState.showSnackbar(it) }
    }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        WifiCameraScreenContent(
            connection = state.connection,
            camera = state.camera,
            bleAvailability = bleAvailability,
            shutter = shutter,
            download = download,
            onSelect = viewModel::select,
            onFocus = viewModel::focus,
            onShoot = viewModel::shoot,
            onCancelFocus = viewModel::cancelFocus,
            onStartDownload = viewModel::startDownload,
            onCancelDownload = viewModel::cancelDownload,
            onConnect = viewModel::connect,
            liveView = {
                // Collected here, inside the slot, rather than up in WifiCameraScreen: a frame
                // arriving at ~30 fps then only recomposes the viewfinder, not the whole screen.
                val liveView by viewModel.liveView.collectAsStateWithLifecycle()
                LiveViewSurface(liveView)
            }
        )

        SnackbarHost(snackbarHostState, Modifier.align(Alignment.BottomCenter))
    }
}

/**
 * Routes to the camera back, the download screen, or the connection panel, purely from state.
 *
 * [liveView] stays a composable slot rather than a plain frame value for the same reason
 * [CameraControlScreen] takes one: whatever drives it can update on its own schedule without
 * forcing this composable to recompose too.
 */
@Composable
internal fun WifiCameraScreenContent(
    connection: WifiCameraConnection,
    camera: CameraSnapshot,
    bleAvailability: WifiHandoverAvailability,
    shutter: ShutterState,
    download: DownloadUiState,
    onSelect: (CameraSettingId, CameraOption) -> Unit,
    onFocus: () -> Unit,
    onShoot: () -> Unit,
    onCancelFocus: () -> Unit,
    onStartDownload: () -> Unit,
    onCancelDownload: () -> Unit,
    onConnect: () -> Unit,
    liveView: @Composable BoxScope.() -> Unit = { LiveViewSurface(LiveViewState.Idle) }
) {
    Box(Modifier.fillMaxSize()) {
        if (connection is WifiCameraConnection.Connected &&
            connection.mode == CameraMode.CONTENTS_TRANSFER
        ) {
            // In this mode the camera offers no live view, settings or shutter — only the images
            // the user picked on the body.
            DownloadScreen(
                state = download,
                onStart = onStartDownload,
                onCancel = onCancelDownload
            )
        } else if (connection is WifiCameraConnection.Connected) {
            CameraControlScreen(
                camera = camera,
                cameraName = connection.camera.friendlyName.ifBlank { connection.camera.modelName },
                onSelect = onSelect,
                onFocus = onFocus,
                onShoot = onShoot,
                onCancelFocus = onCancelFocus,
                shutter = shutter,
                liveView = liveView
            )
        } else {
            ConnectionPanel(
                connection = connection,
                bleAvailability = bleAvailability,
                onConnect = onConnect,
                modifier = Modifier.align(Alignment.Center)
            )
        }
    }
}

private val previewIdentity = CameraIdentity(
    friendlyName = "ILCE-6600",
    modelName = "ILCE-6600",
    udn = null
)

@Preview(name = "Connected — remote shooting", showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun WifiCameraScreenContentShootingPreview() {
    BluetoothRemoteForSonyCamerasTheme {
        WifiCameraScreenContent(
            connection = WifiCameraConnection.Connected(previewIdentity, CameraMode.REMOTE_SHOOTING),
            camera = CameraSnapshot(),
            bleAvailability = WifiHandoverAvailability.UNAVAILABLE,
            shutter = ShutterState.IDLE,
            download = DownloadUiState.Idle,
            onSelect = { _, _ -> },
            onFocus = {},
            onShoot = {},
            onCancelFocus = {},
            onStartDownload = {},
            onCancelDownload = {},
            onConnect = {},
        )
    }
}

@Preview(name = "Connected — download mode", showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun WifiCameraScreenContentDownloadPreview() {
    BluetoothRemoteForSonyCamerasTheme {
        WifiCameraScreenContent(
            connection = WifiCameraConnection.Connected(previewIdentity, CameraMode.CONTENTS_TRANSFER),
            camera = CameraSnapshot(),
            bleAvailability = WifiHandoverAvailability.UNAVAILABLE,
            shutter = ShutterState.IDLE,
            download = DownloadUiState.Running(
                stage = "Downloading 2 of 5",
                fileName = "DSC00042.JPG",
                overallFraction = 0.4f
            ),
            onSelect = { _, _ -> },
            onFocus = {},
            onShoot = {},
            onCancelFocus = {},
            onStartDownload = {},
            onCancelDownload = {},
            onConnect = {},
        )
    }
}

@Preview(name = "Disconnected", showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun WifiCameraScreenContentDisconnectedPreview() {
    BluetoothRemoteForSonyCamerasTheme {
        WifiCameraScreenContent(
            connection = WifiCameraConnection.Idle,
            camera = CameraSnapshot(),
            bleAvailability = WifiHandoverAvailability.UNAVAILABLE,
            shutter = ShutterState.IDLE,
            download = DownloadUiState.Idle,
            onSelect = { _, _ -> },
            onFocus = {},
            onShoot = {},
            onCancelFocus = {},
            onStartDownload = {},
            onCancelDownload = {},
            onConnect = {},
        )
    }
}
