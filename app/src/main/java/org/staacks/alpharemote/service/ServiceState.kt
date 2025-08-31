package org.staacks.alpharemote.service

import org.staacks.alpharemote.camera.CameraState

sealed class ServiceState {

   object Gone : ServiceState()

    data class Running(
        val cameraState: CameraState,
        val countdown: Long?,
        val countdownLabel: String?,
        val pendingTriggerCount: Int = 0,
    ) : ServiceState()
}
