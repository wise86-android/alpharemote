package org.staacks.alpharemote.camera.ble

import android.bluetooth.BluetoothGatt
import android.util.Log
import org.staacks.alpharemote.camera.CameraBLE.Companion.TAG
import java.util.LinkedList

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
    fun onWriteOperationCompleted(status: Int) {
        Log.d(TAG, "Writing complete: $status")
        currentOperation?.let {
            if (it is Write) {
                operationComplete()
                it.resultCallback(status, it.data)
            }
        }
    }

    @androidx.annotation.RequiresPermission(android.Manifest.permission.BLUETOOTH_CONNECT)
    fun onReadOperationCompleted(status: Int, value: ByteArray) {
        Log.d(TAG, "BLEReadComplete: $status, 0x${value.toHexString()}")
        currentOperation?.let {
            if (it is Read) {
                operationComplete()
                it.resultCallback(status, value)
            }
        }
    }

    @androidx.annotation.RequiresPermission(android.Manifest.permission.BLUETOOTH_CONNECT)
    fun onSubscribeOperationComplete(status: Int) {
        Log.d(TAG, "BLESubscribeComplete: $status")
        ///Note: We do not check the status. If subscribing failed for some reason, the camera status is not reported.
        // If this is due to a disconnect, the service will be terminated anyway, but if there is another reason, the
        // rest of the app might still be usable
        if (currentOperation is SubscribeForUpdate) {
            operationComplete()
        }
    }

    @androidx.annotation.RequiresPermission(android.Manifest.permission.BLUETOOTH_CONNECT)
    fun onMtuChange(mtuSize: Int, status: Int) {
        Log.d(TAG, "MTU change: $mtuSize, $status")
        currentOperation?.let {
            if (it is ChangeMtu) {
                operationComplete()
                it.callback(mtuSize, status)
            }
        }

    }


}