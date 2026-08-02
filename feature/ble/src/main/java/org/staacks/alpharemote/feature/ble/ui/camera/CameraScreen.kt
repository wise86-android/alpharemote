package org.staacks.alpharemote.feature.ble.ui.camera
import org.staacks.alpharemote.feature.ble.R

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.material3.rememberStandardBottomSheetState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SheetValue
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.staacks.alpharemote.feature.ble.camera.CameraAction
import org.staacks.alpharemote.feature.ble.camera.CameraActionPreset
import org.staacks.alpharemote.feature.ble.camera.CameraState
import org.staacks.alpharemote.feature.ble.camera.FocusState
import org.staacks.alpharemote.feature.ble.camera.ShutterState
import org.staacks.alpharemote.core.ui.theme.CustomButtonsDrawerPeek
import org.staacks.alpharemote.core.ui.theme.BluetoothRemoteForSonyCamerasTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CameraScreen(
    uiState: CameraViewModel.CameraUIState,
    customButtons: List<CameraAction>,
    onGotoSettings: () -> Unit,
    onGotoWifiCamera: () -> Unit,
    onHelp: () -> Unit,
    onDefaultRemoteTouch: (RemoteButton, Int) -> Boolean,
    onCustomButtonClick: (CameraAction) -> Unit,
) {
    if (!uiState.connected) {
        DisconnectedCameraView(
            remoteDisabled = uiState.remoteDisabled,
            onGotoSettings = onGotoSettings,
            onGotoWifiCamera = onGotoWifiCamera,
        )
        return
    }

    val sheetState = rememberStandardBottomSheetState(
        initialValue = SheetValue.PartiallyExpanded,
        skipHiddenState = true,
    )

    BottomSheetScaffold(
        scaffoldState = rememberBottomSheetScaffoldState(bottomSheetState = sheetState),
        sheetPeekHeight = CustomButtonsDrawerPeek,
        sheetContent = {
            CustomButtonsSheet(
                uiState = uiState,
                customButtons = customButtons,
                onCustomButtonClick = onCustomButtonClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding(),
            )
        },
        sheetContainerColor = colorResource(R.color.gray10),
        sheetSwipeEnabled = true,
        containerColor = Color.Transparent,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 4.dp)
        ) {
            StatusHeader(uiState = uiState, onHelp = onHelp)
            DefaultRemote(
                cameraState = uiState.cameraState,
                onButtonTouch = onDefaultRemoteTouch,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun CameraScreenPreview() {
    BluetoothRemoteForSonyCamerasTheme {
        Surface {
            CameraScreen(
                uiState = CameraViewModel.CameraUIState(
                    connected = true,
                    cameraState = CameraState.Connected.Ready(
                        name = "Alpha 7",
                        address = "00:00:00:00:00:00",
                        focus = FocusState.LOST,
                        shutter = ShutterState.PRESSED,
                        recording = false,
                    ),
                ),
                customButtons = listOf(
                    CameraAction(false, null, CameraActionPreset.SHUTTER),
                    CameraAction(false, null, CameraActionPreset.AF_ON),
                ),
                onGotoSettings = {},
                onGotoWifiCamera = {},
                onHelp = {},
                onDefaultRemoteTouch = { _, _ -> true },
                onCustomButtonClick = {},
            )
        }
    }
}