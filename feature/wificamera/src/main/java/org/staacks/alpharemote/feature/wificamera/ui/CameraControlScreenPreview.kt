package org.staacks.alpharemote.feature.wificamera.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import kotlinx.serialization.json.JsonPrimitive
import org.staacks.alpharemote.feature.wificamera.domain.BatteryInfo
import org.staacks.alpharemote.feature.wificamera.domain.CameraOption
import org.staacks.alpharemote.feature.wificamera.domain.CameraSetting
import org.staacks.alpharemote.feature.wificamera.domain.CameraSettingId
import org.staacks.alpharemote.feature.wificamera.domain.CameraSnapshot
import org.staacks.alpharemote.feature.wificamera.domain.CameraStatus
import org.staacks.alpharemote.feature.wificamera.domain.StorageInfo

/**
 * Previews of the camera back, so the layout can be worked on without a camera on the desk.
 *
 * The values here are plausible α6600 output; the real ones always come from `getEvent`.
 */
private fun setting(
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

private fun exposureCompensation(currentEv: String) = CameraSettingId.EXPOSURE_COMPENSATION to
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

private val connectedCamera = CameraSnapshot(
    status = CameraStatus.IDLE,
    availableApis = setOf(
        "setFNumber", "setIsoSpeedRate", "setShutterSpeed", "setExposureCompensation",
        "setWhiteBalance", "setExposureMode", "actTakePicture"
    ),
    settings = mapOf(
        setting(CameraSettingId.ISO_SPEED_RATE, "400", listOf("AUTO", "100", "200", "400", "800", "1600", "3200")),
        setting(CameraSettingId.F_NUMBER, "2.0", listOf("1.4", "1.8", "2.0", "2.8", "4.0", "5.6", "8.0")),
        setting(CameraSettingId.SHUTTER_SPEED, "1/250", listOf("1/1000", "1/500", "1/250", "1/125", "1/60")),
        exposureCompensation("-0.7"),
        setting(CameraSettingId.EXPOSURE_MODE, "Manual", listOf("Program Auto", "Aperture", "Shutter", "Manual")),
        setting(CameraSettingId.WHITE_BALANCE, "Daylight", listOf("Auto WB", "Daylight", "Shade", "Cloudy")),
        // Read-only on this body in this mode: visible, dimmed, not touchable.
        setting(CameraSettingId.FOCUS_MODE, "AF-C", listOf("AF-S", "AF-C", "DMF", "MF"), writable = false)
    ),
    storage = StorageInfo("Memory Card 1", recordableImages = 3421, recordableTimeMinutes = 95),
    battery = BatteryInfo(levelPercent = 82, status = "active")
)

@Preview(name = "Connected", showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun CameraControlScreenPreview() {
    CameraControlScreen(
        camera = connectedCamera,
        cameraName = "ILCE-6600",
        onSelect = { _, _ -> },
        onCapture = {}
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
        onCapture = {}
    )
}
