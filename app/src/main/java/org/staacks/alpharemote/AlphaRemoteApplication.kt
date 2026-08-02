package org.staacks.alpharemote

import android.app.Application
import org.staacks.alpharemote.core.ble.CameraBleConnection
import org.staacks.alpharemote.feature.wificamera.data.ble.WifiHandover

/**
 * Wires cross-feature BLE contributions together at process start.
 *
 * `feature:ble` drives the camera connection and `feature:wificamera` contributes a standing
 * [org.staacks.alpharemote.core.ble.BleServiceManager] to it (the Wi-Fi handover), but neither
 * feature may depend on the other — only `app`, the composition root, may know about both. This
 * has to run here rather than from `MainActivity` or `feature:ble`'s own service: a paired camera
 * can start `CompanionAlphaRemoteService` directly from Companion Device presence, with no
 * activity ever created first, so registration must happen as early as process start guarantees.
 */
class AlphaRemoteApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        CameraBleConnection.register(WifiHandover.serviceManager())
    }
}
