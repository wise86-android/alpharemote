package org.staacks.alpharemote.camera

sealed class CameraState{

    object Unknown : CameraState()
    object StateNotBonded : CameraState()
    object RemoteDisabled : CameraState()

    data class Ready(
        val name: String,
        val address: String,
        val focus: Boolean,
        val shutter: Boolean,
        val recording: Boolean,
        val pressedButtons: Set<ButtonCode> = emptySet(),
        val pressedJogs: Set<JogCode> = emptySet()
    ) : CameraState() {
        fun applyCommand(command: CameraActionStep): Ready {
            return when (command) {
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

    data class Error(
        val exception: Exception?,
        val description: String = ""
    ) : CameraState()

}