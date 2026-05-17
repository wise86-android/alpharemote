package org.staacks.alpharemote.camera

sealed class CameraState {
    object Disconnected : CameraState()
    object NotBonded : CameraState()

    sealed class Connected : CameraState() {
        object RemoteDisabled : Connected()
        data class Ready(
            val name: String,
            val address: String,
            val focus: Boolean,
            val shutter: Boolean,
            val recording: Boolean,
            val pressedButtons: Set<ButtonCode> = emptySet(),
            val pressedJogs: Set<JogCode> = emptySet(),
            val countdown: Long? = null,
            val countdownLabel: String? = null,
            val pendingTriggerCount: Int = 0
        ) : Connected() {
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
    }

    data class Error(
        val exception: Exception?,
        val description: String = ""
    ) : CameraState()
}