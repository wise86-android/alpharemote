package org.staacks.alpharemote.feature.ble.camera

import java.util.Date

enum class FocusState {
    LOST,
    ACQUIRED,
    SEARCHING,
}

enum class ShutterState {
    PRESSED,
    RELEASED
}

sealed class CameraState {
    object Disconnected : CameraState()
    object NotBonded : CameraState()

    sealed class Connected : CameraState() {
        data class RemoteDisabled(
            val connectedAt: Date,
            val lastLocationSync: Date? = null,
        ) : Connected()
        data class Ready(
            val name: String,
            val address: String,
            val focus: FocusState = FocusState.LOST,
            val shutter: ShutterState = ShutterState.RELEASED,
            val recording: Boolean,
            val pressedButtons: Set<ButtonCode> = emptySet(),
            val pressedJogs: Set<JogCode> = emptySet(),
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