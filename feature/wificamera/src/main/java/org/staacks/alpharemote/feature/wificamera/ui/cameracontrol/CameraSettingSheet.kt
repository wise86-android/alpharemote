package org.staacks.alpharemote.feature.wificamera.ui.cameracontrol

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.staacks.alpharemote.feature.wificamera.domain.CameraOption
import org.staacks.alpharemote.feature.wificamera.domain.CameraSetting
import org.staacks.alpharemote.feature.wificamera.domain.CameraSettingId
import org.staacks.alpharemote.feature.wificamera.ui.theme.CameraColors
import org.staacks.alpharemote.feature.wificamera.ui.theme.CameraType

/**
 * One sheet for any setting — the only difference between ISO and white balance is the list of
 * values, which comes from the camera. The drum itself lives in [DrumPicker].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CameraSettingSheet(
    setting: CameraSetting,
    onSelect: (CameraOption) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val selectedIndex = setting.available.indexOfFirst { it.label == setting.current?.label }
        .coerceAtLeast(0)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = CameraColors.SurfaceElevated,
        dragHandle = { BottomSheetDefaults.DragHandle(color = CameraColors.Divider) }
    ) {
        Column(Modifier.fillMaxWidth().padding(bottom = 28.dp)) {
            Text(
                text = setting.id.label.uppercase(),
                style = CameraType.label,
                modifier = Modifier.padding(horizontal = 20.dp)
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = CameraValueFormat.sheetValue(setting.id, setting.current?.label),
                style = CameraType.hudLarge,
                modifier = Modifier.padding(horizontal = 20.dp)
            )
            Spacer(Modifier.height(20.dp))

            DrumPicker(
                options = setting.available,
                selectedIndex = selectedIndex,
                onSettled = { index -> setting.available.getOrNull(index)?.let(onSelect) }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Preview(showBackground = true, widthDp = 390, heightDp = 400)
@Composable
private fun CameraSettingSheetPreview() {
    CameraSettingSheet(
        setting = CameraSetting(
            id = CameraSettingId.ISO_SPEED_RATE,
            current = CameraOption.of("400"),
            available = listOf("AUTO", "100", "200", "400", "800", "1600", "3200")
                .map { CameraOption.of(it) },
            writable = true
        ),
        onSelect = {},
        onDismiss = {}
    )
}
