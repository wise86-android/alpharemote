package org.staacks.alpharemote.feature.wificamera.ui.cameracontrol

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.staacks.alpharemote.core.ui.theme.BluetoothRemoteForSonyCamerasTheme
import org.staacks.alpharemote.feature.wificamera.domain.CameraOption
import org.staacks.alpharemote.feature.wificamera.domain.CameraSetting
import org.staacks.alpharemote.feature.wificamera.domain.CameraSettingId

/** Dimmed and inert when the camera offers no setter for this value in its current mode. */
@Composable
internal fun SecondaryChip(
    icon: ImageVector,
    setting: CameraSetting?,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val editable = setting?.writable == true && setting.available.isNotEmpty()
    Surface(
        onClick = onClick,
        enabled = editable,
        shape = RoundedCornerShape(8.dp),
        color = Color.Transparent,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier.height(32.dp).alpha(if (editable) 1f else 0.38f)
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                icon,
                null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(14.dp)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = setting?.current?.label ?: "--",
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun SecondaryChipPreview() {
    BluetoothRemoteForSonyCamerasTheme {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(16.dp)) {
            SecondaryChip(
                icon = Icons.Filled.CenterFocusStrong,
                setting = CameraSetting(
                    id = CameraSettingId.FOCUS_MODE,
                    current = CameraOption.of("AF-C"),
                    available = listOf("AF-S", "AF-C", "DMF", "MF").map { CameraOption.of(it) },
                    writable = true
                ),
                onClick = {}
            )
            // No setter in this mode — dimmed and untappable.
            SecondaryChip(
                icon = Icons.Filled.WbSunny,
                setting = CameraSetting(
                    id = CameraSettingId.WHITE_BALANCE,
                    current = CameraOption.of("Daylight"),
                    available = emptyList(),
                    writable = false
                ),
                onClick = {}
            )
        }
    }
}
