package org.staacks.alpharemote.camera.ble

import android.bluetooth.BluetoothGattCharacteristic

sealed class BLEOperation

data class BLEWrite(
    val characteristic: BluetoothGattCharacteristic,
    val data: ByteArray
) : BLEOperation() {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as BLEWrite

        if (characteristic != other.characteristic) return false
        if (!data.contentEquals(other.data)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = characteristic.hashCode()
        result = 31 * result + data.contentHashCode()
        return result
    }
}

data class BLERead(
    val characteristic: BluetoothGattCharacteristic,
    val resultCallback: (Int, ByteArray) -> Unit
) : BLEOperation()

data class BLESubscribe(
    val characteristic: BluetoothGattCharacteristic
) : BLEOperation()