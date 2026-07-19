package org.staacks.alpharemote.camera.ble

import android.Manifest
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.util.Log
import androidx.annotation.RequiresPermission
import org.staacks.alpharemote.camera.CameraBLE.Companion.TAG
import java.util.UUID

sealed interface BLEOperation{
    val hightPriority: Boolean
    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun execute(gatt: BluetoothGatt)
}

data class Write(
    val characteristic: BluetoothGattCharacteristic,
    val data: ByteArray,
    val resultCallback: (Int, ByteArray) -> Unit = {_,_ -> }
) : BLEOperation {
    override val hightPriority: Boolean = false
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Write

        if (characteristic != other.characteristic) return false
        if (!data.contentEquals(other.data)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = characteristic.hashCode()
        result = 31 * result + data.contentHashCode()
        return result
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    override fun execute(gatt: BluetoothGatt) {
        Log.d(TAG, "Writing: 0x${data.toHexString()}")
        gatt.writeCharacteristic(characteristic, data, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT)
    }
}

data class Read(
    val characteristic: BluetoothGattCharacteristic,
    val resultCallback: (Int, ByteArray) -> Unit
) : BLEOperation{
    override val hightPriority: Boolean = false
    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    override fun execute(gatt: BluetoothGatt) {
        Log.d(TAG, "Reading from: ${characteristic.uuid}")
        gatt.readCharacteristic(characteristic)
    }
}

data class SubscribeForUpdate(
    val characteristic: BluetoothGattCharacteristic,
    val resultCallback: (Int) -> Unit = {}
) : BLEOperation{
    override val hightPriority: Boolean = false
    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    override fun execute(gatt: BluetoothGatt) {
        gatt.setCharacteristicNotification(characteristic, true)
        val descriptor = characteristic.getDescriptor(configDescriptorUUID)
        gatt.writeDescriptor(descriptor, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
    }

    companion object{
        private val configDescriptorUUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    }
}

data class ChangeMtu(val mtuSize: Int, val callback:(mtuSize:Int, status:Int)-> Unit): BLEOperation{
    override val hightPriority: Boolean = false
    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    override fun execute(gatt: BluetoothGatt) {
        gatt.requestMtu(mtuSize)
    }
}