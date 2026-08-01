package org.staacks.alpharemote.feature.wificamera.domain

/**
 * Where we are in the handshake described in PROTOCOL.md §1.
 *
 * Getting to [Connected] is a four-stage pipeline and each stage fails in its own way, so the
 * states are distinct rather than a single boolean: "joined the access point but SSDP found
 * nothing" and "never joined the access point" call for very different advice to the user.
 */
sealed interface WifiCameraConnection {

    data object Idle : WifiCameraConnection

    /** Waiting for the user to accept the system dialog for the camera's access point. */
    data object JoiningWifi : WifiCameraConnection

    /** On the camera's network, sending M-SEARCH and listening for NOTIFY. */
    data object Discovering : WifiCameraConnection

    /** Found a device description URL, reading it and negotiating API versions. */
    data object Handshaking : WifiCameraConnection

    data class Connected(
        val camera: CameraIdentity,
        val mode: CameraMode
    ) : WifiCameraConnection

    data class Failed(val reason: FailureReason, val detail: String? = null) : WifiCameraConnection

    val isConnected: Boolean get() = this is Connected
}

enum class FailureReason {
    /** [android.Manifest.permission.NEARBY_WIFI_DEVICES] was not granted. */
    MISSING_PERMISSION,

    /** The user declined the network request, or the access point never appeared. */
    WIFI_JOIN_FAILED,

    /** Joined, but no Sony device answered SSDP. */
    CAMERA_NOT_FOUND,

    /**
     * A device answered, but its address is not in the app's cleartext allowlist, so the request
     * would be refused by the platform before it ever left the phone.
     *
     * Its own reason rather than a generic network error because the fix is a specific one-line
     * edit, and the detail carries the address that needs adding.
     */
    CLEARTEXT_BLOCKED,

    /**
     * Found a camera, but it is not offering the remote shooting API — almost always because the
     * body is in "Send to Smartphone" rather than "Ctrl w/ Smartphone" (PROTOCOL.md §1.1). The
     * user has to change this on the camera; there is no API for it.
     */
    WRONG_CAMERA_MODE,

    /** Description says PTP/IP. A different transport entirely, out of scope for this module. */
    UNSUPPORTED_PROTOCOL,

    NETWORK_ERROR
}

/**
 * Which of the camera's two worlds we have connected to.
 *
 * Set on the body, not over the API — the α6600 has no `setCameraFunction`. Switching restarts
 * the camera's access point and re-advertises a completely different service set, so this is
 * discovered afresh on every connection rather than remembered (PROTOCOL.md §1.1).
 */
enum class CameraMode {
    /** "Ctrl w/ Smartphone": live view, settings, shutter. */
    REMOTE_SHOOTING,

    /** "Send to Smartphone": the images the user picked on the body are waiting to be pulled. */
    CONTENTS_TRANSFER
}

/**
 * The camera we are talking to, as it described itself.
 */
data class CameraIdentity(
    val friendlyName: String,
    val modelName: String,
    val udn: String?
)

/**
 * Credentials for the camera's access point.
 *
 * Kept as a value rather than read from a constant at the point of use so that the BLE handover
 * of PROTOCOL.md §6 — which reads SSID from characteristic `0000CC06` and the password from
 * `0000CC07` — can supply these later without touching the connection code.
 */
data class WifiCredentials(
    val ssid: String,
    val password: String
)
