package org.staacks.alpharemote.ui.camera

import org.staacks.alpharemote.camera.CameraAction
import org.staacks.alpharemote.camera.CameraActionPreset

enum class RemoteButton {
    SHUTTER,
    SHUTTER_HALF,
    SELFTIMER_3S,
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
        RemoteButton.SELFTIMER_3S -> CameraActionPreset.TRIGGER_ONCE
        RemoteButton.RECORD -> CameraActionPreset.RECORD
        RemoteButton.C1 -> CameraActionPreset.C1
        RemoteButton.AF_ON -> CameraActionPreset.AF_ON
        RemoteButton.ZOOM_IN -> CameraActionPreset.ZOOM_IN
        RemoteButton.ZOOM_OUT -> CameraActionPreset.ZOOM_OUT
        RemoteButton.FOCUS_FAR -> CameraActionPreset.FOCUS_FAR
        RemoteButton.FOCUS_NEAR -> CameraActionPreset.FOCUS_NEAR
    }
    val selfTimer = if (this == RemoteButton.SELFTIMER_3S) 3.0f else null
    return CameraAction(
        toggle = this in setOf(RemoteButton.SHUTTER_HALF, RemoteButton.AF_ON),
        selfTimer = selfTimer,
        duration = null,
        step = null,
        preset = preset
    )
}
