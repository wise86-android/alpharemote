package org.staacks.alpharemote.ui.camera

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.material3.rememberStandardBottomSheetState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SheetValue
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.staacks.alpharemote.R
import org.staacks.alpharemote.camera.CameraAction
import org.staacks.alpharemote.camera.CameraActionPreset
import org.staacks.alpharemote.camera.CameraState
import org.staacks.alpharemote.service.ServiceState
import org.staacks.alpharemote.ui.theme.AdvancedControlsDrawerPeek
import org.staacks.alpharemote.ui.theme.BluetoothRemoteForSonyCamerasTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CameraScreen(
    uiState: CameraViewModel.CameraUIState,
    customButtons: List<CameraAction>,
    onGotoSettings: () -> Unit,
    onHelp: () -> Unit,
    onDefaultRemoteTouch: (DefaultRemoteButton.Button, Int) -> Boolean,
    onBulbToggleChanged: (Boolean) -> Unit,
    onBulbDurationChanged: (String) -> Unit,
    onIntervalToggleChanged: (Boolean) -> Unit,
    onIntervalCountChanged: (String) -> Unit,
    onIntervalDurationChanged: (String) -> Unit,
    onStartSequence: () -> Unit,
    onCustomButtonClick: (CameraAction) -> Unit,
) {
    if (!uiState.connected) {
        DisconnectedCameraContent(onGotoSettings = onGotoSettings)
        return
    }

    val sheetState = rememberStandardBottomSheetState(
        initialValue = SheetValue.PartiallyExpanded,
        skipHiddenState = true,
    )

    BottomSheetScaffold(
        scaffoldState = rememberBottomSheetScaffoldState(bottomSheetState = sheetState),
        sheetPeekHeight = AdvancedControlsDrawerPeek,
        sheetContent = {
            AdvancedControlsSheet(
                uiState = uiState,
                customButtons = customButtons,
                onBulbToggleChanged = onBulbToggleChanged,
                onBulbDurationChanged = onBulbDurationChanged,
                onIntervalToggleChanged = onIntervalToggleChanged,
                onIntervalCountChanged = onIntervalCountChanged,
                onIntervalDurationChanged = onIntervalDurationChanged,
                onStartSequence = onStartSequence,
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

@Composable
private fun DisconnectedCameraContent(onGotoSettings: () -> Unit) {
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
                text = stringResource(R.string.camera_not_connected),
                modifier = Modifier.width(400.dp),
            )
            Spacer(modifier = Modifier.height(30.dp))
            Button(onClick = onGotoSettings) {
                Icon(
                    painter = painterResource(R.drawable.ic_settings_black_24dp),
                    contentDescription = null,
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = stringResource(R.string.title_settings))
            }
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
                    serviceState = ServiceState.Running(
                        cameraState = CameraState.Ready(
                            name = "Alpha 7",
                            address = "00:00:00:00:00:00",
                            focus = true,
                            shutter = false,
                            recording = false,
                        ),
                        countdown = null,
                        countdownLabel = null,
                    ),
                    cameraState = CameraState.Ready(
                        name = "Alpha 7",
                        address = "00:00:00:00:00:00",
                        focus = true,
                        shutter = false,
                        recording = false,
                    ),
                ),
                customButtons = listOf(
                    CameraAction(false, null, null, null, CameraActionPreset.SHUTTER),
                    CameraAction(false, null, null, null, CameraActionPreset.AF_ON),
                ),
                onGotoSettings = {},
                onHelp = {},
                onDefaultRemoteTouch = { _, _ -> true },
                onBulbToggleChanged = {},
                onBulbDurationChanged = {},
                onIntervalToggleChanged = {},
                onIntervalCountChanged = {},
                onIntervalDurationChanged = {},
                onStartSequence = {},
                onCustomButtonClick = {},
            )
        }
    }
}
