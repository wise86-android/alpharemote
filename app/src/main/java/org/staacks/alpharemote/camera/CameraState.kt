package org.staacks.alpharemote.camera

sealed class CameraState

class CameraStateGone : CameraState()
class CameraStateConnecting : CameraState()

class CameraStateNotBonded : CameraState()
class CameraStateRemoteDisabled : CameraState()

data class CameraStateReady(
    val name: String,
    val address: String,
    val focus: Boolean,
    val shutter: Boolean,
    val recording: Boolean,
    val pressedButtons: Set<ButtonCode> = emptySet(),
    val pressedJogs: Set<JogCode> = emptySet()
) : CameraState(){
    fun applyCommand(command:CameraActionStep):CameraStateReady{
        return when(command){
            is CAButton -> copy(
                pressedButtons = if (command.pressed)
                    pressedButtons + command.button
                else
                    pressedButtons - command.button
            )
            is CAJog -> copy(
                pressedJogs = if (command.pressed) pressedJogs + command.jog else pressedJogs - command.jog
            )
            else -> this
        }
    }
}

data class CameraStateError(
    val exception: Exception?,
    val description: String = ""
) : CameraState()
