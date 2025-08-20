package org.staacks.alpharemote.camera

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.location.Location
import android.os.Build
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.staacks.alpharemote.camera.ble.BleCommandQueue
import org.staacks.alpharemote.camera.ble.BleConnectionState
import org.staacks.alpharemote.camera.ble.ChangeMtu
import org.staacks.alpharemote.camera.ble.Disconnect
import org.staacks.alpharemote.camera.ble.GenericAccessService
import org.staacks.alpharemote.camera.ble.LocationService
import org.staacks.alpharemote.camera.ble.RemoteControlService
import org.staacks.alpharemote.camera.ble.logAllCharacteristics
import java.util.Date


// Massive thanks to coral for the documentation of the camera's BLE protocol at
// https://github.com/coral/freemote
// and to Greg Leeds at
// https://gregleeds.com/reverse-engineering-sony-camera-bluetooth/

class CameraBLE(
    private val device: BluetoothDevice
) {

    private val _cameraConnectionState = MutableStateFlow(BleConnectionState.Idle)
    val connectionState = _cameraConnectionState.asStateFlow()

    private var gatt: BluetoothGatt? = null
    private var bleOperationQueue: BleCommandQueue?=null

    private val genericAccessService = GenericAccessService()
    private val remoteControlService = RemoteControlService()
    private val locationService = LocationService()
    private var managedService = listOf(
        genericAccessService,locationService,remoteControlService
    )

    val deviceAddress: String
        get() = device.address

    val deviceName = genericAccessService.deviceName
    val deviceStatus = remoteControlService.deviceStatus
    val remoteCommandStatus = remoteControlService.commandStatus

    private val bluetoothGattCallback = object : BluetoothGattCallback() {

        @androidx.annotation.RequiresPermission(android.Manifest.permission.BLUETOOTH_CONNECT)
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            super.onConnectionStateChange(gatt, status, newState)
            Log.d(TAG, "onConnectionStateChange: status $status, newState $newState")
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                bleOperationQueue = BleCommandQueue(gatt)
                bleOperationQueue?.enqueueOperation(ChangeMtu(PREFERRED_CONNECTION_MTU,{ newMtu,status ->
                    Log.d(TAG, "MTU change: $newMtu, $status")
                    gatt.discoverServices()
                }))
                _cameraConnectionState.update { BleConnectionState.Connected }
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                notifyDisconnect()
            }
        }

        @androidx.annotation.RequiresPermission(android.Manifest.permission.BLUETOOTH_CONNECT)
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            super.onServicesDiscovered(gatt, status)
            Log.d(TAG, "onServicesDiscovered")
            if (status == BluetoothGatt.GATT_SUCCESS) {
                logAllCharacteristics(gatt,TAG)
                bleOperationQueue?.let { commandQueue ->
                    managedService.forEach { it.onConnect(gatt, commandQueue) }
                }
            } else {
                Log.e(TAG, "discovery failed: $status")
                _cameraConnectionState.update { BleConnectionState.ErrorDuringConnection }
                //Note, at this point the service will not be usable, but we stay connected as this might be recoverable.
                //In fact, newer cameras seem to send an onServiceChanged to bonded devices after few ms, which triggers Android to restart discovery.
                //If this was the reason for this discovery to fail, onServiceChanged will be called soon where discoverServices will be called again.
            }
        }

        @androidx.annotation.RequiresPermission(android.Manifest.permission.BLUETOOTH_CONNECT)
        override fun onServiceChanged(gatt: BluetoothGatt) {
            super.onServiceChanged(gatt)
            Log.d(TAG, "onServiceChanged")
            bleOperationQueue?.resetOperationQueue()
            gatt.discoverServices()
        }

        @androidx.annotation.RequiresPermission(android.Manifest.permission.BLUETOOTH_CONNECT)
        override fun onCharacteristicWrite(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            super.onCharacteristicWrite(gatt, characteristic, status)
            bleOperationQueue?.onWriteOperationCompleted(characteristic,status)
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
            bleOperationQueue?.onReadOperationCompleted(status, characteristic, value)
        }

        @androidx.annotation.RequiresPermission(android.Manifest.permission.BLUETOOTH_CONNECT)
        override fun onDescriptorWrite(
            gatt: BluetoothGatt,
            descriptor: BluetoothGattDescriptor,
            status: Int
        ) {
            super.onDescriptorWrite(gatt, descriptor, status)
            bleOperationQueue?.onSubscribeOperationComplete(status,descriptor.characteristic)
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
            managedService.forEach { it.onCharacteristicsChanged(characteristic, value) }
        }

        @androidx.annotation.RequiresPermission(android.Manifest.permission.BLUETOOTH_CONNECT)
        override fun onMtuChanged(gatt: BluetoothGatt?, mtu: Int, status: Int) {
            super.onMtuChanged(gatt, mtu, status)
            bleOperationQueue?.onMtuChange(mtu, status)
        }
    }

    fun updateBondedState(context: Context, newState: Int) {
        if (connectionState.value == BleConnectionState.Connected && newState != BluetoothDevice.BOND_BONDED) {
            _cameraConnectionState.update { BleConnectionState.BoundLost }
            Log.e(TAG, "Camera became unbonded while in use.")
        } else if (_cameraConnectionState.value == BleConnectionState.BoundLost && newState == BluetoothDevice.BOND_BONDED) {
            Log.e(TAG, "Camera is now bonded.")
            connectToDevice(context)
        }
    }

    @androidx.annotation.RequiresPermission(android.Manifest.permission.BLUETOOTH_CONNECT)
    fun connectToDevice(context: Context) {
        Log.d(TAG, "connectToDevice")
        if (device.bondState == BluetoothDevice.BOND_BONDED) {
            _cameraConnectionState.update { BleConnectionState.Connecting }
            gatt = device.connectGatt(context, true, bluetoothGattCallback)
        } else {
            _cameraConnectionState.update { BleConnectionState.BoundLost }
            Log.e(TAG, "Camera found, but not bonded. yet")
        }
    }

    private fun notifyDisconnect() {
        Log.d(TAG, "notifyDisconnect")
        bleOperationQueue?.resetOperationQueue()
        _cameraConnectionState.update { BleConnectionState.Disconnected }
    }


    @androidx.annotation.RequiresPermission(android.Manifest.permission.BLUETOOTH_CONNECT)
    fun disconnectFromDevice() {
        Log.d(TAG, "disconnectFromDevice")
        bleOperationQueue?.enqueueOperation(Disconnect)
    }
    @androidx.annotation.RequiresPermission(android.Manifest.permission.BLUETOOTH_CONNECT)
    fun executeCameraActionStep(action: CameraActionStep) {
        Log.d(TAG, "executeCameraActionStep")
        if (_cameraConnectionState.value !== BleConnectionState.Connected)
            return
        remoteControlService.sendCommand(action)
    }

    @androidx.annotation.RequiresPermission(android.Manifest.permission.BLUETOOTH_CONNECT)
    fun setCameraLocation(location: Location){
        locationService.updateLocationAndTime(location, Date())
    }

    companion object {
        const val TAG = "AlphaRemote-BLE"
        const val PREFERRED_CONNECTION_MTU = 153
    }
}