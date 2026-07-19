package org.staacks.alpharemote.camera.ble

import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic

import android.util.Log
import kotlinx.coroutines.suspendCancellableCoroutine
import org.staacks.alpharemote.camera.CameraBLE.Companion.TAG
import java.util.LinkedList
import kotlin.coroutines.resume

class BleCommandQueue(private val gatt: BluetoothGatt) {
    private val operationQueue = LinkedList<BLEOperation>()
    private var currentOperation: BLEOperation? = null

    @Synchronized
    @androidx.annotation.RequiresPermission(android.Manifest.permission.BLUETOOTH_CONNECT)
    fun enqueueOperation(operation: BLEOperation) {
        if(operation.hightPriority){
            operationQueue.add(0, operation)
        }else {
            operationQueue.add(operation)
        }
        if (currentOperation == null) {
            executeNextOperation()
        }
    }

    @Synchronized
    fun resetOperationQueue() {
        operationQueue.clear()
        currentOperation = null
    }

    // Suspend wrappers around the callback-based queue. They resume when the corresponding GATT
    // callback completes the operation. If the queue is reset (disconnect), the continuation is
    // never resumed - callers are expected to run in a connection-scoped coroutine that gets
    // cancelled on disconnect.

    @androidx.annotation.RequiresPermission(android.Manifest.permission.BLUETOOTH_CONNECT)
    suspend fun write(characteristic: BluetoothGattCharacteristic, data: ByteArray): Int =
        suspendCancellableCoroutine { continuation ->
            enqueueOperation(Write(characteristic, data) { status, _ ->
                continuation.resume(status)
            })
        }

    @androidx.annotation.RequiresPermission(android.Manifest.permission.BLUETOOTH_CONNECT)
    suspend fun read(characteristic: BluetoothGattCharacteristic): Pair<Int, ByteArray> =
        suspendCancellableCoroutine { continuation ->
            enqueueOperation(Read(characteristic) { status, value ->
                continuation.resume(status to value)
            })
        }

    @androidx.annotation.RequiresPermission(android.Manifest.permission.BLUETOOTH_CONNECT)
    suspend fun subscribe(characteristic: BluetoothGattCharacteristic): Int =
        suspendCancellableCoroutine { continuation ->
            enqueueOperation(SubscribeForUpdate(characteristic) { status ->
                continuation.resume(status)
            })
        }

    @Synchronized
    @androidx.annotation.RequiresPermission(android.Manifest.permission.BLUETOOTH_CONNECT)
    private fun executeNextOperation() {
        if (currentOperation != null)
            return
        currentOperation = operationQueue.poll()
        currentOperation?.execute(gatt)
    }

    @Synchronized
    @androidx.annotation.RequiresPermission(android.Manifest.permission.BLUETOOTH_CONNECT)
    private fun operationComplete() {
        currentOperation = null
        executeNextOperation()
    }


    @androidx.annotation.RequiresPermission(android.Manifest.permission.BLUETOOTH_CONNECT)
    fun onWriteOperationCompleted(gattCharacteristic: BluetoothGattCharacteristic,status: Int) {
        Log.d(TAG, "Writing complete: $status")
        currentOperation?.let {
            if (it is Write && it.characteristic.uuid == gattCharacteristic.uuid) {
                operationComplete()
                it.resultCallback(status, it.data)
            }
        }
    }

    @androidx.annotation.RequiresPermission(android.Manifest.permission.BLUETOOTH_CONNECT)
    fun onReadOperationCompleted(status: Int, gattCharacteristic: BluetoothGattCharacteristic, value: ByteArray) {
        Log.d(TAG, "BLEReadComplete: $status, 0x${value.toHexString()}")
        currentOperation?.let {
            if (it is Read && gattCharacteristic.uuid == it.characteristic.uuid) {
                operationComplete()
                it.resultCallback(status, value)
            }
        }
    }

    @androidx.annotation.RequiresPermission(android.Manifest.permission.BLUETOOTH_CONNECT)
    fun onSubscribeOperationComplete(status: Int, gattCharacteristic: BluetoothGattCharacteristic) {
        Log.d(TAG, "BLESubscribeComplete: $status")
        ///Note: We do not check the status. If subscribing failed for some reason, the camera status is not reported.
        // If this is due to a disconnect, the service will be terminated anyway, but if there is another reason, the
        // rest of the app might still be usable
        currentOperation?.let {
            if (it is SubscribeForUpdate && it.characteristic == gattCharacteristic) {
                operationComplete()
                it.resultCallback(status)
            }
        }
    }

    @androidx.annotation.RequiresPermission(android.Manifest.permission.BLUETOOTH_CONNECT)
    fun onMtuChange(mtuSize: Int, status: Int) {
        Log.d(TAG, "onMtuChange: $mtuSize, $status")
        currentOperation?.let {
            if (it is ChangeMtu) {
                operationComplete()
                it.callback(mtuSize, status)
            }
        }

    }


}