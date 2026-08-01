package org.staacks.alpharemote.feature.wificamera.ui.cameracontrol

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryStd
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.staacks.alpharemote.feature.wificamera.domain.BatteryInfo
import org.staacks.alpharemote.feature.wificamera.domain.CameraSnapshot
import org.staacks.alpharemote.feature.wificamera.domain.StorageInfo
import org.staacks.alpharemote.feature.wificamera.ui.theme.CameraColors
import org.staacks.alpharemote.feature.wificamera.ui.theme.CameraType

/** The connection scrim at the top of [CameraControlScreen]: camera name, shots left, battery. */
@Composable
internal fun StatusBar(camera: CameraSnapshot, cameraName: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .background(
                Brush.verticalGradient(listOf(CameraColors.OverlayTop, Color.Transparent))
            )
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Filled.Wifi,
            contentDescription = "Connected",
            tint = CameraColors.AccentTeal,
            modifier = Modifier.size(16.dp)
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = cameraName,
            style = CameraType.label,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false)
        )

        Spacer(Modifier.weight(1f))

        // Both are optional in the protocol: older bodies never report them, so they are simply
        // absent rather than shown as zero.
        camera.storage?.recordableImages?.let {
            Text("$it", style = CameraType.hudSmallDim)
            Spacer(Modifier.width(12.dp))
        }
        camera.battery?.levelPercent?.let {
            Icon(
                imageVector = Icons.Filled.BatteryStd,
                contentDescription = "Battery",
                tint = CameraColors.TextSecondary,
                modifier = Modifier.size(16.dp)
            )
            Spacer(Modifier.width(2.dp))
            Text("$it%", style = CameraType.hudSmallDim)
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0D0D0F)
@Composable
private fun StatusBarPreview() {
    StatusBar(
        camera = CameraSnapshot(
            storage = StorageInfo("Memory Card 1", recordableImages = 3421, recordableTimeMinutes = 95),
            battery = BatteryInfo(levelPercent = 82, status = "active")
        ),
        cameraName = "ILCE-6600"
    )
}

/** Older bodies report neither shots remaining nor battery; both are absent, not zero. */
@Preview(name = "No storage or battery reported", showBackground = true, backgroundColor = 0xFF0D0D0F)
@Composable
private fun StatusBarMinimalPreview() {
    StatusBar(camera = CameraSnapshot(), cameraName = "ILCE-6600")
}
