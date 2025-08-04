package org.staacks.alpharemote.camera

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.staacks.alpharemote.camera.ble.BleCommandQueue
import org.staacks.alpharemote.camera.ble.Disconnect
import org.staacks.alpharemote.camera.ble.FocusState
import org.staacks.alpharemote.camera.ble.Write
import org.staacks.alpharemote.camera.ble.GenericAccessService
import org.staacks.alpharemote.camera.ble.RemoteControlService
import org.staacks.alpharemote.camera.ble.ShutterState
import java.util.UUID

// Massive thanks to coral for the documentation of the camera's BLE protocol at
// https://github.com/coral/freemote
// and to Greg Leeds at
// https://gregleeds.com/reverse-engineering-sony-camera-bluetooth/



class CameraBLE(
    val scope: CoroutineScope,
    context: Context,
    val address: String,
    val onConnect: () -> Unit,
    val onDisconnect: () -> Unit
) {

    val remoteServiceUUID = UUID.fromString("8000ff00-ff00-ffff-ffff-ffffffffffff")!!
    val commandCharacteristicUUID = UUID.fromString("0000ff01-0000-1000-8000-00805f9b34fb")!!
    val statusCharacteristicUUID = UUID.fromString("0000ff02-0000-1000-8000-00805f9b34fb")!!

    var remoteService: BluetoothGattService? = null
    var commandCharacteristic: BluetoothGattCharacteristic? = null
    var statusCharacteristic: BluetoothGattCharacteristic? = null


    private val _cameraState = MutableStateFlow<CameraState>(CameraStateGone())
    val cameraState: StateFlow<CameraState> = _cameraState.asStateFlow()

    private var bluetoothAdapter: BluetoothAdapter = ContextCompat.getSystemService(context, BluetoothManager::class.java)!!.adapter


    private var device: BluetoothDevice? = null
    private var gatt: BluetoothGatt? = null

    private val bleOperationQueue = BleCommandQueue()
    private val genericAccessService = GenericAccessService(bleOperationQueue)
    private val remoteControlService = RemoteControlService(bleOperationQueue)

    init {
        scope.launch {
            genericAccessService.deviceName.collect { newName ->
                _cameraState.value = CameraStateIdentified(newName, address)
            }
            remoteControlService.deviceStatus.collect { newStatus ->
                _cameraState.update {
                    when(it){
                        is CameraStateReady -> it.copy(
                            focus = newStatus.focus === FocusState.ACQUIRED,
                            shutter = newStatus.shutter === ShutterState.PRESSED,
                            recording = newStatus.isRecording
                        )
                        is CameraStateRemoteDisabled ->
                            CameraStateReady(
                                genericAccessService.deviceName.value,
                                focus = newStatus.focus === FocusState.ACQUIRED,
                                shutter = newStatus.shutter === ShutterState.PRESSED,
                                recording = newStatus.isRecording,
                                emptySet(),
                                emptySet()
                            )
                        else -> it
                    }
                }
            }
        }
    }

    private val bluetoothGattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
            Log.d(TAG, "onConnectionStateChange: status $status, newState $newState")
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                bleOperationQueue.gatt = gatt
                onConnect()
                try {
                    gatt?.discoverServices()
                } catch (e: SecurityException) {
                    Log.e(TAG, e.toString())
                    _cameraState.value = CameraStateError(e)
                }
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                bleOperationQueue.gatt = null
                notifyDisconnect()
            }
        }

        @androidx.annotation.RequiresPermission(android.Manifest.permission.BLUETOOTH_CONNECT)
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            Log.d(TAG, "onServicesDiscovered")
            if (status == BluetoothGatt.GATT_SUCCESS) {
                genericAccessService.attach(gatt)
                remoteControlService.attach(gatt)

                remoteService = gatt.getService(remoteServiceUUID)
                commandCharacteristic = remoteService?.getCharacteristic(commandCharacteristicUUID)
                statusCharacteristic = remoteService?.getCharacteristic(statusCharacteristicUUID)
            } else {
                Log.e(TAG, "discovery failed: $status")
                _cameraState.value = CameraStateError(null, "Service discovery failed.")
                //Note, at this point the service will not be usable, but we stay connected as this might be recoverable.
                //In fact, newer cameras seem to send an onServiceChanged to bonded devices after few ms, which triggers Android to restart discovery.
                //If this was the reason for this discovery to fail, onServiceChanged will be called soon where discoverServices will be called again.
            }
        }

        override fun onServiceChanged(gatt: BluetoothGatt) {
            super.onServiceChanged(gatt)
            Log.d(TAG, "onServiceChanged")
            bleOperationQueue.resetOperationQueue()
            try {
                gatt.discoverServices()
            } catch (e: SecurityException) {
                Log.e(TAG, e.toString())
                _cameraState.value = CameraStateError(e)
            }
        }

        @androidx.annotation.RequiresPermission(android.Manifest.permission.BLUETOOTH_CONNECT)
        override fun onCharacteristicWrite(
            gatt: BluetoothGatt?,
            characteristic: BluetoothGattCharacteristic?,
            status: Int
        ) {
            super.onCharacteristicWrite(gatt, characteristic, status)
            bleOperationQueue.onWriteOperationCompleted(status)
        }

        @Suppress("DEPRECATION", "Used for backwards compatibility on API<33")
        @androidx.annotation.RequiresPermission(android.Manifest.permission.BLUETOOTH_CONNECT)
        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            super.onCharacteristicRead(gatt, characteristic, status)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                return //Use the new version of onCharacteristicRead instead
            Log.d(
                TAG,
                "Deprecated onCharacteristicRead with status $status from ${characteristic.uuid}."
            )
            this.onCharacteristicRead(gatt, characteristic, characteristic.value,status)
        }

        @androidx.annotation.RequiresPermission(android.Manifest.permission.BLUETOOTH_CONNECT)
        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
            status: Int
        ) {
            super.onCharacteristicRead(gatt, characteristic, value, status)
            Log.d(TAG, "onCharacteristicRead with status $status from ${characteristic.uuid}.")
            bleOperationQueue.onReadOperationCompleted(status, value)
        }

        @androidx.annotation.RequiresPermission(android.Manifest.permission.BLUETOOTH_CONNECT)
        override fun onDescriptorWrite(
            gatt: BluetoothGatt?,
            descriptor: BluetoothGattDescriptor?,
            status: Int
        ) {
            super.onDescriptorWrite(gatt, descriptor, status)
            bleOperationQueue.onSubscribeOperationComplete(status)
        }


        @Deprecated("Deprecated in Java")
        @Suppress("DEPRECATION", "Used for backwards compatibility on API<33")
        @androidx.annotation.RequiresPermission(android.Manifest.permission.BLUETOOTH_CONNECT)
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            super.onCharacteristicChanged(gatt, characteristic)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                return //Use the new version of onCharacteristicRead instead
            Log.d(TAG, "Deprecated onCharacteristicChanged from ${characteristic.uuid}.")
           this.onCharacteristicChanged(gatt,characteristic,characteristic.value)
        }

        @androidx.annotation.RequiresPermission(android.Manifest.permission.BLUETOOTH_CONNECT)
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            super.onCharacteristicChanged(gatt, characteristic, value)
            Log.d(TAG, "onCharacteristicChanged from ${characteristic.uuid}.")
            remoteControlService.onCharacteristicChanged(characteristic, value)

        }
    }

    private val bondStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            Log.d(TAG, "BLE received BluetoothDevice.ACTION_BOND_STATE_CHANGED.")
            try {
                if (cameraState.value is CameraStateReady && device?.bondState != BluetoothDevice.BOND_BONDED) {
                    _cameraState.value = CameraStateNotBonded()
                    Log.e(TAG, "Camera became unbonded while in use.")
                } else if (cameraState.value is CameraStateNotBonded && device?.bondState == BluetoothDevice.BOND_BONDED) {
                    Log.e(TAG, "Camera is now bonded.")
                    connectToDevice(context)
                }
            } catch (e: SecurityException) {
                _cameraState.value = CameraStateError(e, e.toString())
                Log.e(TAG, e.toString())
            }
        }
    }

    init {
        Log.d(TAG, "init")
        context.registerReceiver(
            bondStateReceiver,
            IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
        )
        connectToDevice(context)
    }

    fun connectToDevice(context: Context) {
        Log.d(TAG, "connectToDevice")
        try {
            device = bluetoothAdapter.getRemoteDevice(address)
            if (device?.bondState == BluetoothDevice.BOND_BONDED) {
                _cameraState.value = CameraStateConnecting()
                gatt = device?.connectGatt(context, true, bluetoothGattCallback)
            } else {
                _cameraState.value = CameraStateNotBonded()
                Log.e(TAG, "Camera found, but not bonded.")
            }
        } catch (e: SecurityException) {
            _cameraState.value = CameraStateError(e, e.toString())
            Log.e(TAG, e.toString())
        }
    }

    fun notifyDisconnect() {
        Log.d(TAG, "notifyDisconnect")
        _cameraState.value = CameraStateGone()
        remoteService = null
        commandCharacteristic = null
        statusCharacteristic = null
        onDisconnect()
    }


    @androidx.annotation.RequiresPermission(android.Manifest.permission.BLUETOOTH_CONNECT)
    fun disconnectFromDevice() {
        Log.d(TAG, "disconnectFromDevice")
        bleOperationQueue.enqueueOperation(Disconnect)
    }

    fun executeCameraActionStep(action: CameraActionStep) {
        Log.d(TAG, "executeCameraActionStep")
        if (cameraState.value !is CameraStateReady)
            return
        try {
            commandCharacteristic?.let { char ->
                when (action) {
                    is CAButton -> {
                        bleOperationQueue.enqueueOperation(
                            Write(
                                char,
                                byteArrayOf(0x01, action.getCode()),
                                { status, data ->
                                    if (status != BluetoothGatt.GATT_SUCCESS) {
                                        //The command failed. This is very likely a properly bonded camera with BLE remote setting disabled
                                        scope.launch {
                                            _cameraState.emit(CameraStateRemoteDisabled())
                                        }
                                    }
                                })
                        )
                        _cameraState.update {
                            (it as? CameraStateReady)?.copy(
                                pressedButtons = if (action.pressed) it.pressedButtons + action.button else it.pressedButtons - action.button
                            ) ?: it
                        }
                    }

                    is CAJog -> {
                        bleOperationQueue.enqueueOperation(
                            Write(
                                char,
                                byteArrayOf(
                                    0x02,
                                    action.getCode(),
                                    if (action.pressed) action.step else 0x00
                                ),
                                { status, data ->
                                    if (status != BluetoothGatt.GATT_SUCCESS) {
                                        //The command failed. This is very likely a properly bonded camera with BLE remote setting disabled
                                        scope.launch {
                                            _cameraState.emit(CameraStateRemoteDisabled())
                                        }
                                    }
                                })
                        )
                        _cameraState.update {
                            (it as? CameraStateReady)?.copy(
                                pressedJogs = if (action.pressed) it.pressedJogs + action.jog else it.pressedJogs - action.jog
                            ) ?: it
                        }
                    }

                    else -> Unit //Countdown and wait for event are handled by service
                }
            }
        } catch (e: SecurityException) {
            _cameraState.value = CameraStateError(e)
            Log.e(TAG, e.toString())
        }
    }

    companion object {
        const val TAG = "AlphaRemote-BLE"
    }
}