package org.staacks.alpharemote.camera

import android.bluetooth.BluetoothGattCharacteristic

sealed class CameraBLEOperation

data class CameraBLEWrite(
    val characteristic: BluetoothGattCharacteristic,
    val data: ByteArray
) : CameraBLEOperation() {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as CameraBLEWrite

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

data class CameraBLERead(
    val characteristic: BluetoothGattCharacteristic,
    val resultCallback: (Int, ByteArray) -> Unit
) : CameraBLEOperation()

data class CameraBLESubscribe(
    val characteristic: BluetoothGattCharacteristic
) : CameraBLEOperation()