package org.staacks.alpharemote.feature.wificamera.ui.cameracontrol

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.staacks.alpharemote.feature.wificamera.domain.CameraOption
import org.staacks.alpharemote.feature.wificamera.domain.CameraSetting
import org.staacks.alpharemote.feature.wificamera.domain.CameraSettingId
import org.staacks.alpharemote.feature.wificamera.ui.theme.CameraColors
import org.staacks.alpharemote.feature.wificamera.ui.theme.CameraType
import org.staacks.alpharemote.feature.wificamera.ui.theme.DISABLED_ALPHA

/** Quiet and icon-free — the mode is set once per outing, not once per shot. */
@Composable
internal fun ModeQuickButton(setting: CameraSetting?, onClick: () -> Unit) {
    val editable = setting?.writable == true && setting.available.isNotEmpty()
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(CameraColors.ChipIdle)
            .border(1.dp, CameraColors.Divider, RoundedCornerShape(8.dp))
            .clickable(enabled = editable, onClick = onClick)
            .alpha(if (editable) 1f else DISABLED_ALPHA),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = CameraValueFormat.chipValue(
                CameraSettingId.EXPOSURE_MODE,
                setting?.current?.label
            ),
            style = CameraType.hudMedium,
            color = CameraColors.TextSecondary,
            maxLines = 1
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0D0D0F)
@Composable
private fun ModeQuickButtonPreview() {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(16.dp)) {
        ModeQuickButton(
            setting = CameraSetting(
                id = CameraSettingId.EXPOSURE_MODE,
                current = CameraOption.of("Manual"),
                available = listOf("Program Auto", "Aperture", "Shutter", "Manual")
                    .map { CameraOption.of(it) },
                writable = true
            ),
            onClick = {}
        )
        // Nothing reported yet — shows a placeholder rather than a guess.
        ModeQuickButton(setting = null, onClick = {})
    }
}
