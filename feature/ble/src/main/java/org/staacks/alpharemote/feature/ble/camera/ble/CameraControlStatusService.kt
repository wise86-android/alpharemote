package org.staacks.alpharemote.feature.ble.camera.ble

import android.Manifest
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattService
import android.util.Log
import androidx.annotation.RequiresPermission
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.staacks.alpharemote.core.ble.BleCommandQueue
import org.staacks.alpharemote.core.ble.BleServiceManager
import java.util.UUID

/**
 * Live "is the camera's own Bluetooth remote control setting on" status, read from the `CC09`
 * characteristic of the camera control service (`8000CC00`, PROTOCOL.md §6.1).
 *
 * This is the authoritative source for that question — unlike watching command writes fail
 * ([RemoteControlService.commandStatus]), it is pushed by the camera as soon as the state is
 * known (or changes), so [AlphaRemoteService][org.staacks.alpharemote.feature.ble.service.AlphaRemoteService]
 * can show `RemoteDisabled` from the start of a connection instead of only after the user's first
 * button press silently fails.
 *
 * Lives in `app` rather than `feature:wificamera`, even though `8000CC00` is the same GATT
 * service that feature owns for the Wi-Fi handover (`CC05`-`CC08`): "is the remote-control
 * feature usable" is that feature's question to answer, not the Wi-Fi handover's, and a second
 * manager subscribing to a different characteristic of the same service is unremarkable at the
 * GATT level — [org.staacks.alpharemote.core.ble.CameraBLE] fans a notification out to every
 * attached manager regardless of who subscribed to it.
 */
class CameraControlStatusService : BleServiceManager {

    private var statusCharacteristic: BluetoothGattCharacteristic? = null

    private val _remoteControlAvailable = MutableStateFlow<Boolean?>(null)
    val remoteControlAvailable: StateFlow<Boolean?> = _remoteControlAvailable.asStateFlow()

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    override fun onConnect(gatt: BluetoothGatt, bleCommandQueue: BleCommandQueue, scope: CoroutineScope) {
        val service = gatt.getService(SERVICE_UUID)
        if (service == null) {
            Log.d(TAG, "Camera does not offer the camera control service")
            return
        }
        val status = service.characteristicByUuidPrefix(STATUS_PREFIX)
        if (status == null) {
            Log.w(TAG, "Camera control service is missing the CC09 status characteristic")
            return
        }
        statusCharacteristic = status
        scope.launch { bleCommandQueue.subscribe(status) }
    }

    override fun onDisconnect() {
        statusCharacteristic = null
        _remoteControlAvailable.value = null
    }

    override fun onCharacteristicsChanged(
        characteristic: BluetoothGattCharacteristic,
        newValue: ByteArray
    ) {
        if (characteristic.uuid != statusCharacteristic?.uuid) return
        CameraControlStatusParsing.remoteControlAvailable(newValue)?.let {
            _remoteControlAvailable.value = it
        }
    }

    private fun BluetoothGattService.characteristicByUuidPrefix(
        prefix: String
    ): BluetoothGattCharacteristic? =
        characteristics.firstOrNull { it.uuid.toString().startsWith(prefix, ignoreCase = true) }

    companion object {
        private const val TAG = "CameraControlStatusService"

        val SERVICE_UUID: UUID = UUID.fromString("8000CC00-CC00-FFFF-FFFF-FFFFFFFFFFFF")
        private const val STATUS_PREFIX = "0000cc09"
    }
}
