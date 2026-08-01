package org.staacks.alpharemote.feature.wificamera.ui.cameracontrol

/**
 * What the shutter button is doing.
 *
 * Focus and capture are distinct steps against the camera, and a photographer holding the button
 * needs to see which one they are in.
 */
enum class ShutterState {
    IDLE,

    /** Half-pressed: the camera is focusing, or has focused and is waiting. */
    FOCUSING,

    /** The shutter has been released and the camera is working. */
    CAPTURING
}
