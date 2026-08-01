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
