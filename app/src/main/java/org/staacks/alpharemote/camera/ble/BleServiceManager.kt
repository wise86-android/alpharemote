package org.staacks.alpharemote.camera.ble

import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import kotlinx.coroutines.CoroutineScope

interface BleServiceManager {
    /**
     * Called after service discovery. [scope] is bound to the GATT connection and cancelled on
     * disconnect - use it to run suspend operations against [bleCommandQueue].
     */
    fun onConnect(gatt: BluetoothGatt, bleCommandQueue: BleCommandQueue, scope: CoroutineScope)
    fun onDisconnect()
    fun onCharacteristicsChanged(characteristic: BluetoothGattCharacteristic, newValue: ByteArray)
}
