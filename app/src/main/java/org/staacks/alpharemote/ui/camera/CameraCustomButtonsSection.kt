package org.staacks.alpharemote.ui.camera

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import org.staacks.alpharemote.R
import org.staacks.alpharemote.camera.CameraAction
import org.staacks.alpharemote.camera.CameraActionPreset
import org.staacks.alpharemote.camera.CameraState
import org.staacks.alpharemote.camera.FocusState
import org.staacks.alpharemote.camera.ShutterState
import org.staacks.alpharemote.ui.theme.BluetoothRemoteForSonyCamerasTheme
import org.staacks.alpharemote.ui.theme.CustomButtonHeightInActivity
import org.staacks.alpharemote.ui.theme.Fulvous
import org.staacks.alpharemote.ui.theme.Gray50
import org.staacks.alpharemote.ui.theme.White

@Composable
fun CustomButtonsSheet(
    uiState: CameraViewModel.CameraUIState,
    customButtons: List<CameraAction>,
    onCustomButtonClick: (CameraAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .background(colorResource(R.color.gray10))
            .padding(top = 2.dp, bottom = 10.dp, start = 10.dp, end = 10.dp)
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(vertical = 8.dp)
                .width(40.dp)
                .height(4.dp)
                .background(colorResource(R.color.gray60), MaterialTheme.shapes.small)
        )

        CustomButtonsRow(
            customButtons = customButtons,
            cameraState = uiState.cameraState,
            onCustomButtonClick = onCustomButtonClick,
        )
    }
}

@Composable
private fun CustomButtonsRow(
    customButtons: List<CameraAction>,
    cameraState: CameraState.Connected.Ready?,
    onCustomButtonClick: (CameraAction) -> Unit,
) {
    val context = LocalContext.current
    val iconSizePx = with(LocalDensity.current) {
        (CustomButtonHeightInActivity - 20.dp).roundToPx()
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(CustomButtonHeightInActivity)
            .padding(10.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        customButtons.forEach { cameraAction ->
            val tint = customActionTint(cameraAction, cameraState)
            // CameraActionIcon is a plain Drawable (shared with the notification), so it is
            // rasterized once per button at display size and drawn as a regular Image.
            val icon = remember(cameraAction, iconSizePx) {
                cameraAction.getIcon(context).toBitmap(iconSizePx, iconSizePx).asImageBitmap()
            }
            Image(
                bitmap = icon,
                contentDescription = cameraAction.getName(context),
                colorFilter = tint?.let { ColorFilter.tint(it) },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .aspectRatio(1f)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = ripple(bounded = false),
                    ) { onCustomButtonClick(cameraAction) },
            )
        }
    }
}

private fun customActionTint(cameraAction: CameraAction, cameraState: CameraState.Connected.Ready?): Color? {
    if (cameraAction.preset.template.preserveColor) {
        return null
    }

    return when {
        cameraState == null -> Gray50
        cameraAction.preset.template.referenceButton in cameraState.pressedButtons ||
            cameraAction.preset.template.referenceJog in cameraState.pressedJogs -> Fulvous
        else -> White
    }
}

@Preview(showBackground = true, widthDp = 420)
@Composable
private fun CustomButtonsSheetPreview() {
    BluetoothRemoteForSonyCamerasTheme {
        Surface {
            CustomButtonsSheet(
                uiState = CameraViewModel.CameraUIState(
                    connected = true,
                    cameraState = CameraState.Connected.Ready(
                        name = "Alpha 7",
                        address = "00:00:00:00:00:00",
                        focus = FocusState.LOST,
                        shutter = ShutterState.RELEASED,
                        recording = false,
                    ),
                ),
                customButtons = listOf(
                    CameraAction(false, null, CameraActionPreset.SHUTTER),
                    CameraAction(false, null, CameraActionPreset.AF_ON),
                ),
                onCustomButtonClick = {},
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
