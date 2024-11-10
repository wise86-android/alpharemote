package org.staacks.alpharemote.camera

sealed class CameraState

class CameraStateGone : CameraState()
class CameraStateConnecting : CameraState()

class CameraStateNotBonded : CameraState()
class CameraStateRemoteDisabled : CameraState()

data class CameraStateIdentified(
    val name: String,
    val address: String
) : CameraState()

data class CameraStateReady(
    val name: String?,
    val focus: Boolean,
    val shutter: Boolean,
    val recording: Boolean,
    val pressedButtons: Set<ButtonCode>,
    val pressedJogs: Set<JogCode>
) : CameraState()

data class CameraStateError(
    val exception: Exception?,
    val description: String = ""
) : CameraState()
