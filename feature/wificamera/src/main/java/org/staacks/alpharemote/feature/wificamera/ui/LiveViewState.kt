package org.staacks.alpharemote.feature.wificamera.ui

import androidx.compose.ui.graphics.ImageBitmap

/**
 * What the viewfinder should be showing.
 *
 * "No picture yet" and "this body has no live view" look the same on screen unless they are
 * modelled apart — the α6600 answers `startLiveview` but several bodies do not, and a viewfinder
 * that sits blank forever with no explanation is the worst of the possible outcomes.
 */
sealed interface LiveViewState {

    /** Not connected, or the stream is not being collected. */
    data object Idle : LiveViewState

    /** Asked the camera to start; no frame has arrived yet. */
    data object Starting : LiveViewState

    data class Streaming(val frame: ImageBitmap) : LiveViewState

    data class Unavailable(val message: String) : LiveViewState
}

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
