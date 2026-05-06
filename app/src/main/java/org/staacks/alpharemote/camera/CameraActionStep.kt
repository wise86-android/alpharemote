package org.staacks.alpharemote.camera

import kotlinx.serialization.Serializable

@Serializable
sealed class CameraActionStep

@Serializable
data class CACountdown (
    val label: String,
    val duration: Float
) : CameraActionStep()

@Serializable
data class CAWaitFor (
    val target: WaitTarget
) : CameraActionStep()

@Serializable
data class CAButton (
    val pressed: Boolean,
    val button: ButtonCode,
    val isSequenceTrigger: Boolean = false,
) : CameraActionStep()

@Serializable
data class CAJog (
    val pressed: Boolean,
    val step: Byte,
    val jog: JogCode,
) : CameraActionStep()

@Serializable
enum class WaitTarget {
    FOCUS, SHUTTER, RECORDING
}

@Serializable
enum class ButtonCode{
    SHUTTER_FULL,
    SHUTTER_HALF,
    RECORD,
    AF_ON,
    C1
}

@Serializable
enum class JogCode( val minStep: Byte, val maxStep: Byte) {
    ZOOM_IN(0x10, 0x7f),
    ZOOM_OUT( 0x10, 0x7f),
    FOCUS_NEAR( 0x00, 0x7f),
    FOCUS_FAR( 0x00, 0x7f)
}
