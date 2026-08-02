package org.staacks.alpharemote.feature.wificamera.data.ble

import kotlinx.coroutines.flow.StateFlow
import org.staacks.alpharemote.core.ble.BleServiceManager
import org.staacks.alpharemote.feature.wificamera.domain.WifiCredentials

/**
 * The module's entry point for reaching the camera's Wi-Fi over BLE.
 *
 * A Kotlin `object` — the single [WifiHandoverService] instance behind it is what makes
 * [serviceManager] idempotent: calling it more than once, or registering its result more than
 * once, always refers to the same manager, which is what a *standing*
 * [org.staacks.alpharemote.core.ble.CameraBleConnection] registration requires.
 *
 * The app wires this in once, at composition, by registering [serviceManager]'s result with
 * `CameraBleConnection`. This module never does that itself — it has no opinion on when a BLE
 * connection to the camera should exist, only on what to do with one when it does.
 */
object WifiHandover {

    private val service = WifiHandoverService()

    fun serviceManager(): BleServiceManager = service

    /** Whether a camera is reachable over BLE right now — not whether Wi-Fi is on. */
    val availability: StateFlow<WifiHandoverAvailability> = service.availability

    /**
     * Turns the camera's Wi-Fi on and returns fresh credentials for it.
     *
     * Only call this in response to the user asking to connect — writing to `CC08` is what turns
     * the camera's Wi-Fi radio on, and doing that unprompted every time a BLE connection happens
     * to exist would run the camera's battery down for no reason.
     */
    @androidx.annotation.RequiresPermission(android.Manifest.permission.BLUETOOTH_CONNECT)
    suspend fun activateWifi(): Result<WifiCredentials> = service.activateWifi()
}
