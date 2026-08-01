package org.staacks.alpharemote.feature.wificamera.ui.cameracontrol

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlinx.serialization.json.JsonPrimitive
import org.staacks.alpharemote.feature.wificamera.domain.BatteryInfo
import org.staacks.alpharemote.feature.wificamera.domain.CameraOption
import org.staacks.alpharemote.feature.wificamera.domain.CameraSetting
import org.staacks.alpharemote.feature.wificamera.domain.CameraSettingId
import org.staacks.alpharemote.feature.wificamera.domain.CameraSnapshot
import org.staacks.alpharemote.feature.wificamera.domain.CameraStatus
import org.staacks.alpharemote.feature.wificamera.domain.StorageInfo
import org.staacks.alpharemote.feature.wificamera.ui.liveview.LiveViewPlaceholder
import org.staacks.alpharemote.feature.wificamera.ui.theme.CameraColors

/**
 * The camera back: live view behind a HUD, values along the bottom.
 *
 * Everything shown comes from the camera's own report — there are no preset value lists, because
 * what is selectable changes with the body and the shooting mode. A setting the camera has not
 * mentioned shows as `--` rather than a guess, and a setting with no setter in the current mode
 * is visible but not touchable.
 *
 * The pieces it assembles live in their own files: [StatusBar], [ExposureMeterStrip],
 * [BottomControlBar] and, inside that, [SecondaryChip], [ExposureChip], [ShutterButton],
 * [ThumbnailButton], [ModeQuickButton]. The setting picker is [CameraSettingSheet].
 *
 * [liveView] is a slot so the video stream can be dropped in later without this file changing.
 */
@Composable
fun CameraControlScreen(
    camera: CameraSnapshot,
    cameraName: String,
    onSelect: (CameraSettingId, CameraOption) -> Unit,
    onFocus: () -> Unit,
    onShoot: () -> Unit,
    onCancelFocus: () -> Unit,
    shutter: ShutterState = ShutterState.IDLE,
    modifier: Modifier = Modifier,
    liveView: @Composable BoxScope.() -> Unit = { LiveViewPlaceholder() }
) {
    var editing by remember { mutableStateOf<CameraSettingId?>(null) }

    Box(modifier.fillMaxSize().background(CameraColors.Background)) {
        Box(Modifier.fillMaxSize()) { liveView() }

        StatusBar(
            camera = camera,
            cameraName = cameraName,
            modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth()
        )

        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth()) {
            ExposureMeterStrip(
                setting = camera[CameraSettingId.EXPOSURE_COMPENSATION],
                onSelect = { option ->
                    onSelect(CameraSettingId.EXPOSURE_COMPENSATION, option)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, CameraColors.SurfaceElevated)
                        )
                    )
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            )
            BottomControlBar(
                camera = camera,
                onFieldTap = { editing = it },
                onFocus = onFocus,
                onShoot = onShoot,
                onCancelFocus = onCancelFocus,
                shutter = shutter
            )
        }
    }

    editing?.let { id ->
        camera[id]?.let { setting ->
            CameraSettingSheet(
                setting = setting,
                onSelect = { option -> onSelect(id, option) },
                onDismiss = { editing = null }
            )
        }
    }
}

/**
 * Previews of the camera back, so the layout can be worked on without a camera on the desk.
 *
 * The values here are plausible α6600 output; the real ones always come from `getEvent`.
 */
private fun previewSetting(
    id: CameraSettingId,
    current: String,
    values: List<String>,
    writable: Boolean = true
) = id to CameraSetting(
    id = id,
    current = CameraOption.of(current),
    available = values.map { CameraOption.of(it) },
    writable = writable
)

private fun previewExposureCompensation(currentEv: String) = CameraSettingId.EXPOSURE_COMPENSATION to
    CameraSetting(
        id = CameraSettingId.EXPOSURE_COMPENSATION,
        current = CameraOption(currentEv, JsonPrimitive(-2)),
        available = (-9..9).map { index ->
            CameraOption(
                label = "%+.1f".format(index / 3.0).replace("+0.0", "0.0"),
                param = JsonPrimitive(index)
            )
        },
        writable = true
    )

private val previewConnectedCamera = CameraSnapshot(
    status = CameraStatus.IDLE,
    availableApis = setOf(
        "setFNumber", "setIsoSpeedRate", "setShutterSpeed", "setExposureCompensation",
        "setWhiteBalance", "setExposureMode", "actTakePicture"
    ),
    settings = mapOf(
        previewSetting(CameraSettingId.ISO_SPEED_RATE, "400", listOf("AUTO", "100", "200", "400", "800", "1600", "3200")),
        previewSetting(CameraSettingId.F_NUMBER, "2.0", listOf("1.4", "1.8", "2.0", "2.8", "4.0", "5.6", "8.0")),
        previewSetting(CameraSettingId.SHUTTER_SPEED, "1/250", listOf("1/1000", "1/500", "1/250", "1/125", "1/60")),
        previewExposureCompensation("-0.7"),
        previewSetting(CameraSettingId.EXPOSURE_MODE, "Manual", listOf("Program Auto", "Aperture", "Shutter", "Manual")),
        previewSetting(CameraSettingId.WHITE_BALANCE, "Daylight", listOf("Auto WB", "Daylight", "Shade", "Cloudy")),
        // Read-only on this body in this mode: visible, dimmed, not touchable.
        previewSetting(CameraSettingId.FOCUS_MODE, "AF-C", listOf("AF-S", "AF-C", "DMF", "MF"), writable = false)
    ),
    storage = StorageInfo("Memory Card 1", recordableImages = 3421, recordableTimeMinutes = 95),
    battery = BatteryInfo(levelPercent = 82, status = "active")
)

@Preview(name = "Connected", showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun CameraControlScreenPreview() {
    CameraControlScreen(
        camera = previewConnectedCamera,
        cameraName = "ILCE-6600",
        onSelect = { _, _ -> },
        onFocus = {},
        onShoot = {},
        onCancelFocus = {}
    )
}

/**
 * What the screen looks like before the camera has reported anything — every value a dash, and
 * nothing touchable. Worth previewing, because it is the first thing seen on connect.
 */
@Preview(name = "Nothing reported yet", showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun CameraControlScreenEmptyPreview() {
    CameraControlScreen(
        camera = CameraSnapshot(),
        cameraName = "ILCE-6600",
        onSelect = { _, _ -> },
        onFocus = {},
        onShoot = {},
        onCancelFocus = {}
    )
}
