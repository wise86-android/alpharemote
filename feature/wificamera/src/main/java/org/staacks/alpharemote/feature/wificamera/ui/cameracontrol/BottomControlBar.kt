package org.staacks.alpharemote.feature.wificamera.ui.cameracontrol

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.staacks.alpharemote.core.ui.theme.BluetoothRemoteForSonyCamerasTheme
import org.staacks.alpharemote.feature.wificamera.domain.CameraOption
import org.staacks.alpharemote.feature.wificamera.domain.CameraSetting
import org.staacks.alpharemote.feature.wificamera.domain.CameraSettingId
import org.staacks.alpharemote.feature.wificamera.domain.CameraSnapshot

/** The chip stack and shutter row anchored to the bottom of [CameraControlScreen]. */
@Composable
internal fun BottomControlBar(
    camera: CameraSnapshot,
    onFieldTap: (CameraSettingId) -> Unit,
    onFocus: () -> Unit,
    onShoot: () -> Unit,
    onCancelFocus: () -> Unit,
    shutter: ShutterState
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                MaterialTheme.colorScheme.surfaceContainerHigh,
                RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
            )
            .padding(top = 14.dp, bottom = 12.dp)
    ) {
        // Set occasionally rather than per shot, so these stay quiet outlined chips instead of
        // sharing the amber the exposure values get.
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SecondaryChip(
                icon = Icons.Filled.CenterFocusStrong,
                setting = camera[CameraSettingId.FOCUS_MODE],
                modifier = Modifier.weight(1f),
                onClick = { onFieldTap(CameraSettingId.FOCUS_MODE) }
            )
            // The mockup's second chip is metering, which the legacy API does not expose on any
            // version. White balance is the nearest thing the camera will actually report.
            SecondaryChip(
                icon = Icons.Filled.WbSunny,
                setting = camera[CameraSettingId.WHITE_BALANCE],
                modifier = Modifier.weight(1f),
                onClick = { onFieldTap(CameraSettingId.WHITE_BALANCE) }
            )
        }

        Spacer(Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ExposureChip(
                label = "ISO",
                setting = camera[CameraSettingId.ISO_SPEED_RATE],
                modifier = Modifier.weight(1f),
                onClick = { onFieldTap(CameraSettingId.ISO_SPEED_RATE) }
            )
            ExposureChip(
                label = "APERTURE",
                setting = camera[CameraSettingId.F_NUMBER],
                modifier = Modifier.weight(1f),
                onClick = { onFieldTap(CameraSettingId.F_NUMBER) }
            )
            ExposureChip(
                label = "SHUTTER",
                setting = camera[CameraSettingId.SHUTTER_SPEED],
                modifier = Modifier.weight(1f),
                onClick = { onFieldTap(CameraSettingId.SHUTTER_SPEED) }
            )
        }

        Spacer(Modifier.height(18.dp))

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 28.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            ThumbnailButton()
            ShutterButton(
                enabled = camera.canShoot,
                shutter = shutter,
                focusStatus = camera.focusStatus,
                onFocus = onFocus,
                onShoot = onShoot,
                onCancel = onCancelFocus
            )
            ModeQuickButton(
                setting = camera[CameraSettingId.EXPOSURE_MODE],
                onClick = { onFieldTap(CameraSettingId.EXPOSURE_MODE) }
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun BottomControlBarPreview() {
    fun setting(id: CameraSettingId, current: String, values: List<String>) = id to CameraSetting(
        id = id,
        current = CameraOption.of(current),
        available = values.map { CameraOption.of(it) },
        writable = true
    )

    BluetoothRemoteForSonyCamerasTheme {
        BottomControlBar(
            camera = CameraSnapshot(
                availableApis = setOf(
                    "setFNumber", "setIsoSpeedRate", "setShutterSpeed", "setWhiteBalance", "actTakePicture"
                ),
                settings = mapOf(
                    setting(CameraSettingId.FOCUS_MODE, "AF-C", listOf("AF-S", "AF-C", "DMF", "MF")),
                    setting(CameraSettingId.WHITE_BALANCE, "Daylight", listOf("Auto WB", "Daylight")),
                    setting(CameraSettingId.ISO_SPEED_RATE, "400", listOf("100", "400", "800")),
                    setting(CameraSettingId.F_NUMBER, "2.0", listOf("1.8", "2.0", "2.8")),
                    setting(CameraSettingId.SHUTTER_SPEED, "1/250", listOf("1/125", "1/250", "1/500"))
                )
            ),
            onFieldTap = {},
            onFocus = {},
            onShoot = {},
            onCancelFocus = {},
            shutter = ShutterState.IDLE
        )
    }
}
