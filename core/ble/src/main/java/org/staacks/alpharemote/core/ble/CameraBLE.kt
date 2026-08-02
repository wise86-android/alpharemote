package org.staacks.alpharemote.core.ble

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update


// Massive thanks to coral for the documentation of the camera's BLE protocol at
// https://github.com/coral/freemote
// and to Greg Leeds at
// https://gregleeds.com/reverse-engineering-sony-camera-bluetooth/

/**
 * Drives one camera's GATT connection: bonding, MTU negotiation, service discovery, and fanning
 * callbacks out to whichever [BleServiceManager]s the caller registers.
 *
 * Deliberately knows nothing about what those managers do. The caller constructs its own service
 * managers (Sony's remote-control service, location service, whatever a future feature needs),
 * keeps its own references to read their state from, and hands the list here only so this class
 * can dispatch connect/disconnect/characteristic-changed events to them.
 */
class CameraBLE(
    private val device: BluetoothDevice,
    private val managedService: List<BleServiceManager>
) {

    private val _cameraConnectionState = MutableStateFlow(BleConnectionState.Idle)
    val connectionState = _cameraConnectionState.asStateFlow()

    private var gatt: BluetoothGatt? = null
    private var bleOperationQueue: BleCommandQueue?=null

    // Bound to the GATT connection, cancelled on disconnect. Handed to the service managers for
    // their suspend BLE operations.
    private var connectionScope: CoroutineScope? = null

    val deviceAddress: String
        get() = device.address

    private val bluetoothGattCallback = object : BluetoothGattCallback() {

        @androidx.annotation.RequiresPermission(android.Manifest.permission.BLUETOOTH_CONNECT)
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            super.onConnectionStateChange(gatt, status, newState)
            Log.d(TAG, "onConnectionStateChange: status $status, newState $newState")
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                connectionScope?.cancel()
                connectionScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
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
                    connectionScope?.let { scope ->
                        managedService.forEach { it.onConnect(gatt, commandQueue, scope) }
                    }
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

    @androidx.annotation.RequiresPermission(android.Manifest.permission.BLUETOOTH_CONNECT)
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
            device.createBond()
            Log.e(TAG, "Camera found, but not bonded. yet")
        }
    }

    @androidx.annotation.RequiresPermission(android.Manifest.permission.BLUETOOTH_CONNECT)
    private fun notifyDisconnect() {
        Log.d(TAG, "notifyDisconnect")
        connectionScope?.cancel()
        connectionScope = null
        managedService.forEach { it.onDisconnect() }
        bleOperationQueue?.resetOperationQueue()
        gatt?.disconnect()
        gatt?.close()
        gatt = null
        bleOperationQueue = null
        _cameraConnectionState.update { BleConnectionState.Disconnected }
    }


    @androidx.annotation.RequiresPermission(android.Manifest.permission.BLUETOOTH_CONNECT)
    fun disconnectFromDevice() {
        Log.d(TAG, "disconnectFromDevice")
        // Tear down directly instead of enqueuing a disconnect operation:
        // - Before the GATT connection is established (still connecting, waiting for bonding or
        //   a failed attempt) there is no operation queue at all.
        // - Once gatt.close() has been called, the STATE_DISCONNECTED callback is not guaranteed
        //   to be delivered anymore, so an enqueued disconnect may never emit the Disconnected
        //   state that the service relies on for cleanup.
        // In both cases this CameraBLE instance would linger and block future connection attempts.
        notifyDisconnect()
    }

    companion object {
        const val TAG = "AlphaRemote-BLE"

        // One MTU for every attached service, since it is negotiated once for the whole GATT
        // connection rather than per-service. 158 is what PROTOCOL.md §6 documents for the
        // location/pairing/Wi-Fi-handover services; the remote-control service was previously
        // tuned to 153, and a larger MTU only ever gives it more room, never less.
        const val PREFERRED_CONNECTION_MTU = 158
    }
}