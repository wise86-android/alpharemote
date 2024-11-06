package org.staacks.alpharemote.camera

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.experimental.and

// Massive thanks to coral for the documentation of the camera's BLE protocol at
// https://github.com/coral/freemote
// and to Greg Leeds at
// https://gregleeds.com/reverse-engineering-sony-camera-bluetooth/

class CameraBLE(val scope: CoroutineScope, context: Context, val address: String, val onDisconnect: () -> Unit) {

    val genericAccessServiceUUID = UUID.fromString("00001800-0000-1000-8000-00805f9b34fb")!!
    val nameCharacteristicUUID = UUID.fromString("00002a00-0000-1000-8000-00805f9b34fb")!!

    val remoteServiceUUID = UUID.fromString("8000ff00-ff00-ffff-ffff-ffffffffffff")!!
    val commandCharacteristicUUID = UUID.fromString("0000ff01-0000-1000-8000-00805f9b34fb")!!
    val statusCharacteristicUUID = UUID.fromString("0000ff02-0000-1000-8000-00805f9b34fb")!!

    private val configDescriptorUUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")!!

    var remoteService: BluetoothGattService? = null
    var commandCharacteristic: BluetoothGattCharacteristic? = null
    var statusCharacteristic: BluetoothGattCharacteristic? = null

    private val operationQueue = ConcurrentLinkedQueue<CameraBLEOperation>()
    private var currentOperation: CameraBLEOperation? = null

    private val _cameraState = MutableStateFlow<CameraState>(CameraStateGone())
    val cameraState: StateFlow<CameraState> = _cameraState.asStateFlow()

    //Since the properties of CameraStateReady might be updated rapidly in parallel, we might run
    //into concurrency issues when updating them all in a single object. Instead, keep track of them
    //individually and assemble them into a CameraStateReady object when they are emitted. Worst
    //case, the same up-to-date state is emitted twice.

    private var isReady = false
    private var name: String? = null
    private var pressedButtons: Set<ButtonCode> = emptySet()
    private var pressedJogs: Set<JogCode> = emptySet()
    private var stateFocus: Boolean = true
    private var stateShutter: Boolean = true
    private var stateRecording: Boolean = true

    private fun emitCameraStateReady() {
        scope.launch {
            if (isReady) {
                _cameraState.emit(CameraStateReady(name, stateFocus, stateShutter, stateRecording, pressedButtons, pressedJogs)
                )
            }
        }
    }

    private var bluetoothAdapter: BluetoothAdapter = BluetoothAdapter.getDefaultAdapter()

    private var device: BluetoothDevice? = null
    private var gatt: BluetoothGatt? = null

    private val bluetoothGattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
            Log.d("BLE", "onConnectionStateChange: status $status, newState $newState")
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                scope.launch {
                    try {
                        gatt?.discoverServices()
                    } catch (e: SecurityException) {
                        isReady = false
                        Log.e("SecurityException", e.toString())
                        scope.launch {
                            _cameraState.emit(CameraStateError(e))
                        }
                    }
                }
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                disconnectFromDevice()
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
            Log.d("BLE", "onServicesDiscovered")
            if (status == BluetoothGatt.GATT_SUCCESS) {
                remoteService = gatt?.getService(remoteServiceUUID)
                commandCharacteristic = remoteService?.getCharacteristic(commandCharacteristicUUID)
                statusCharacteristic = remoteService?.getCharacteristic(statusCharacteristicUUID)
                val nameCharacteristic = gatt?.getService(genericAccessServiceUUID)?.getCharacteristic(nameCharacteristicUUID)
                if (statusCharacteristic != null && commandCharacteristic != null && nameCharacteristic != null) {
                    statusCharacteristic?.let {
                        enqueueOperation(CameraBLERead(nameCharacteristic){
                            scope.launch {
                                val newName = it.toString(Charsets.UTF_8)
                                _cameraState.emit(CameraStateIdentified(newName, address))
                                name = newName
                            }
                        })
                        enqueueOperation(CameraBLESubscribe(it))
                    }
                } else {
                    isReady = false
                    scope.launch {
                        Log.e("serviceNotFound", "remoteService: " + remoteService.toString())
                        Log.e("serviceNotFound", "commandCharacteristic: " + commandCharacteristic.toString())
                        Log.e("serviceNotFound", "statusCharacteristic: " + statusCharacteristic.toString())
                        Log.e("serviceNotFound", "nameCharacteristic: " + nameCharacteristic.toString())
                        _cameraState.emit(CameraStateError(null, "Remote service not found."))
                    }
                    disconnectFromDevice()
                }
            } else {
                Log.e("serviceNotFound", "discovery failed: $status")
                disconnectFromDevice()
            }
        }

        override fun onCharacteristicWrite(
            gatt: BluetoothGatt?,
            characteristic: BluetoothGattCharacteristic?,
            status: Int
        ) {
            super.onCharacteristicWrite(gatt, characteristic, status)
            cameraBLEWriteComplete(status)
        }

        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            super.onCharacteristicRead(gatt, characteristic, status)
            cameraBLEReadComplete(status, characteristic.value)
        }

        override fun onDescriptorWrite(
            gatt: BluetoothGatt?,
            descriptor: BluetoothGattDescriptor?,
            status: Int
        ) {
            super.onDescriptorWrite(gatt, descriptor, status)
            cameraBLESubscribeComplete(status)
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            super.onCharacteristicChanged(gatt, characteristic)
            if (characteristic == statusCharacteristic) {
                onCameraStatusUpdate(characteristic.value)
            }
        }
    }

    init {
        Log.d("BLE", "init / connectToDevice")
        try {
            device = bluetoothAdapter.getRemoteDevice(address)
            if (device?.bondState == BluetoothDevice.BOND_BONDED) {
                gatt = device?.connectGatt(context, false, bluetoothGattCallback)
                scope.launch {
                    _cameraState.emit(CameraStateConnecting())
                }
            } else {
                isReady = false
                Log.e("BLE", "Camera found, but not bonded.")
                scope.launch {
                    _cameraState.emit(CameraStateNotBonded())
                }
            }
        } catch (e: SecurityException) {
            isReady = false
            Log.e("SecurityException", e.toString())
            scope.launch {
                _cameraState.emit(CameraStateError(e, e.toString()))
            }
        }
    }

    fun disconnectFromDevice() {
        Log.d("BLE", "disconnectFromDevice")
        try {
            isReady = false
            gatt?.close()
            gatt = null
            remoteService = null
            commandCharacteristic = null
            statusCharacteristic = null
            operationQueue.clear()
            currentOperation = null
            scope.launch {
                _cameraState.emit(CameraStateGone())
                onDisconnect()
            }
        } catch (e: SecurityException) {
            isReady = false
            Log.e("SecurityException", e.toString())
            scope.launch {
                _cameraState.emit(CameraStateError(e))
                onDisconnect()
            }
        }
    }

    @Synchronized
    fun enqueueOperation(operation: CameraBLEOperation) {
        operationQueue.add(operation)
        if (currentOperation == null) {
            executeNextOperation()
        }
    }

    @OptIn(ExperimentalStdlibApi::class)
    @Synchronized
    fun executeNextOperation() {
        if (currentOperation != null)
            return

        currentOperation = operationQueue.poll()

        try {
            when (currentOperation) {
                is CameraBLEWrite -> {
                    val op = currentOperation as CameraBLEWrite
                    Log.d("BLE", "Writing: " + op.data.toHexString())
                    op.characteristic.setValue(op.data)
                    gatt?.writeCharacteristic(op.characteristic)
                }

                is CameraBLERead -> {
                    val op = currentOperation as CameraBLERead
                    gatt?.readCharacteristic(op.characteristic)
                }
                is CameraBLESubscribe -> {
                    val op = currentOperation as CameraBLESubscribe
                    gatt?.setCharacteristicNotification(op.characteristic, true)
                    val descriptor = op.characteristic.getDescriptor(configDescriptorUUID)
                    descriptor?.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
                    gatt?.writeDescriptor(descriptor)
                }

                else -> return
            }
        } catch (e: SecurityException) {
            isReady = false
            Log.e("SecurityException", e.toString())
            scope.launch {
                _cameraState.emit(CameraStateError(e))
            }
        }
    }

    @Synchronized
    fun operationComplete() {
        currentOperation = null
        executeNextOperation()
    }

    fun cameraBLEWriteComplete(status: Int) {
        Log.d("BLE", "Writing complete: $status")
        if (currentOperation is CameraBLEWrite) {
            operationComplete()
        }
    }

    fun cameraBLEReadComplete(status: Int, value: ByteArray) {
        Log.d("BLE", "cameraBLEReadComplete: $status, $value")
        if (currentOperation is CameraBLERead) {
            val callback = (currentOperation as CameraBLERead).resultCallback
            operationComplete()
            callback(value)
        }
    }

    fun cameraBLESubscribeComplete(status: Int) {
        Log.d("BLE", "cameraBLESubscribeComplete: $status")
        if (currentOperation is CameraBLESubscribe) {
            scope.launch {
                val name = (cameraState.value as? CameraStateIdentified)?.name
                if (name == null)
                    Log.w("BLE", "Subscribe complete, but camera in unidentified state.")
                stateFocus = false
                stateShutter = false
                stateRecording = false
                pressedButtons = emptySet()
                pressedJogs = emptySet()
                isReady = true
                emitCameraStateReady()
            }
            operationComplete()
        }
    }

    @OptIn(ExperimentalStdlibApi::class)
    fun onCameraStatusUpdate(value: ByteArray) {
        if (isReady) {
            when (value[1]) {
                0x3f.toByte() -> stateFocus = (value[2].and(0x20.toByte()) != 0.toByte())
                0xa0.toByte() -> stateShutter = (value[2].and(0x20.toByte()) != 0.toByte())
                0xd5.toByte() -> stateRecording = (value[2].and(0x20.toByte()) != 0.toByte())
            }
            emitCameraStateReady()
        }
        Log.d("BLE", "newStatus: " + value.toHexString())
    }

    fun executeCameraActionStep(action: CameraActionStep) {
        Log.d("BLE", "executeCameraActionStep")
        if (!isReady)
            return
        try {
            commandCharacteristic?.let {
                when (action) {
                    is CAButton -> {
                        enqueueOperation(CameraBLEWrite(it, byteArrayOf(0x01, action.getCode())))
                        pressedButtons = if (action.pressed) pressedButtons + action.button else pressedButtons - action.button
                        emitCameraStateReady()
                    }
                    is CAJog -> {
                        enqueueOperation(CameraBLEWrite(it, byteArrayOf(0x02, action.getCode(), if (action.pressed) action.step else 0x00)))
                        pressedJogs = if (action.pressed) pressedJogs + action.jog else pressedJogs - action.jog
                        emitCameraStateReady()
                    }
                    else -> Unit //Countdown and wait for event are handled by service
                }
            }
        } catch (e: SecurityException) {
            isReady = false
            Log.e("SecurityException", e.toString())
            scope.launch {
                _cameraState.emit(CameraStateError(e))
            }
        }
    }
}