package org.staacks.alpharemote.camera.ble

import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic

interface BleServiceManager {
    fun onConnect(gatt: BluetoothGatt, bleCommandQueue:BleCommandQueue)
    fun onDisconnect()
    fun onCharacteristicsChanged(characteristic: BluetoothGattCharacteristic, newValue: ByteArray)
}