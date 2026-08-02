package org.staacks.alpharemote.feature.ble.ui.camera

import org.staacks.alpharemote.feature.ble.camera.CameraAction
import org.staacks.alpharemote.feature.ble.camera.CameraActionPreset

enum class RemoteButton {
    SHUTTER,
    SHUTTER_HALF,
    TRIGGER_ONCE,
    RECORD,
    C1,
    AF_ON,
    ZOOM_IN,
    ZOOM_OUT,
    FOCUS_FAR,
    FOCUS_NEAR,
}

fun RemoteButton.toCameraAction(): CameraAction {
    val preset = when (this) {
        RemoteButton.SHUTTER -> CameraActionPreset.SHUTTER
        RemoteButton.SHUTTER_HALF -> CameraActionPreset.SHUTTER_HALF
        RemoteButton.TRIGGER_ONCE -> CameraActionPreset.TRIGGER_ONCE
        RemoteButton.RECORD -> CameraActionPreset.RECORD
        RemoteButton.C1 -> CameraActionPreset.C1
        RemoteButton.AF_ON -> CameraActionPreset.AF_ON
        RemoteButton.ZOOM_IN -> CameraActionPreset.ZOOM_IN
        RemoteButton.ZOOM_OUT -> CameraActionPreset.ZOOM_OUT
        RemoteButton.FOCUS_FAR -> CameraActionPreset.FOCUS_FAR
        RemoteButton.FOCUS_NEAR -> CameraActionPreset.FOCUS_NEAR
    }
    return CameraAction(
        toggle = this in setOf(RemoteButton.SHUTTER_HALF, RemoteButton.AF_ON),
        step = null,
        preset = preset
    )
}
