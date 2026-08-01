package org.staacks.alpharemote.feature.wificamera.domain

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * The module's whole surface. ViewModels see nothing below this line — no sockets, no JSON, no
 * `Network` objects.
 *
 * State is pull-free: [connection] and [camera] are always current because a background poll
 * keeps them so, which is what makes a change made on the camera body show up on screen without
 * anyone asking for it.
 */
interface WifiCameraRepository {

    val connection: StateFlow<WifiCameraConnection>

    /**
     * Live camera state. Resets to an empty snapshot on disconnect so stale values can never be
     * mistaken for current ones.
     */
    val camera: StateFlow<CameraSnapshot>

    /**
     * Joins the access point and runs the discovery pipeline. Safe to call when already
     * connecting or connected — it does nothing in that case.
     */
    fun connect(credentials: WifiCredentials)

    fun disconnect()

    /**
     * Pushes a new value for [id]. The returned result reports whether the camera accepted the
     * write; the resulting value arrives separately through [camera], because the camera is the
     * authority on what actually took effect.
     */
    suspend fun setSetting(id: CameraSettingId, option: CameraOption): Result<Unit>

    /**
     * Frames from the camera's live view, newest-only.
     *
     * Cold: the stream starts on the camera when collection starts and stops when it ends. The
     * flow already drops frames the collector is too slow for — decoding must not queue, or
     * latency grows without bound (PROTOCOL.md §3.2).
     */
    fun liveView(): Flow<LiveViewFrame>
}

/**
 * One decoded frame off the live view socket.
 *
 * [jpeg] is a complete JPEG ready for a decoder — the Sony framing has already been stripped.
 */
class LiveViewFrame(
    val jpeg: ByteArray,
    val sequenceNumber: Int,
    val timestampMs: Long
)
