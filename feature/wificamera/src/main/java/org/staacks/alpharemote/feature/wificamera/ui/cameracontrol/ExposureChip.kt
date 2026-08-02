package org.staacks.alpharemote.feature.wificamera.ui.cameracontrol

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.staacks.alpharemote.core.ui.theme.BluetoothRemoteForSonyCamerasTheme
import org.staacks.alpharemote.feature.wificamera.domain.CameraOption
import org.staacks.alpharemote.feature.wificamera.domain.CameraSetting
import org.staacks.alpharemote.feature.wificamera.domain.CameraSettingId

/** One of the three ISO/aperture/shutter chips — the values a photographer changes per shot. */
@Composable
internal fun ExposureChip(
    label: String,
    setting: CameraSetting?,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val editable = setting?.writable == true && setting.available.isNotEmpty()
    Surface(
        onClick = onClick,
        enabled = editable,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = modifier.height(56.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 10.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = setting
                    ?.let { CameraValueFormat.chipValue(it.id, it.current?.label) }
                    ?: "--",
                style = MaterialTheme.typography.bodyMedium,
                color = if (editable) {
                    MaterialTheme.colorScheme.secondary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ExposureChipPreview() {
    BluetoothRemoteForSonyCamerasTheme {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(16.dp)) {
            ExposureChip(
                label = "ISO",
                setting = CameraSetting(
                    id = CameraSettingId.ISO_SPEED_RATE,
                    current = CameraOption.of("400"),
                    available = listOf("100", "400", "800").map { CameraOption.of(it) },
                    writable = true
                ),
                onClick = {}
            )
            ExposureChip(
                label = "APERTURE",
                setting = CameraSetting(
                    id = CameraSettingId.F_NUMBER,
                    current = CameraOption.of("2.0"),
                    available = emptyList(),
                    writable = false
                ),
                onClick = {}
            )
        }
    }
}
