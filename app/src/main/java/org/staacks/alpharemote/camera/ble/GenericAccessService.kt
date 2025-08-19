package org.staacks.alpharemote.camera.ble

import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattService
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.nio.ByteBuffer
import java.nio.ByteOrder.LITTLE_ENDIAN
import java.util.UUID

class GenericAccessService : BleServiceManager{

    private val _deviceNameFlow = MutableStateFlow(DEFAULT_DEVICE_NAME)
    val deviceName: StateFlow<String> = _deviceNameFlow.asStateFlow()

    private val _preferredConnectionParametersFlow =
        MutableStateFlow<PreferredConnectionParameters?>(null)
    val preferredConnectionParametersFlow: StateFlow<PreferredConnectionParameters?> =
        _preferredConnectionParametersFlow.asStateFlow()

    private lateinit var bleOperationQueue: BleCommandQueue

    @androidx.annotation.RequiresPermission(android.Manifest.permission.BLUETOOTH_CONNECT)
    override fun onConnect(gatt: BluetoothGatt, bleCommandQueue: BleCommandQueue) {
        this.bleOperationQueue = bleCommandQueue
        val genericAccessService = gatt.getService(SERVICE_UUID)
        genericAccessService?.let { service ->
            readDeviceName(service, bleOperationQueue)
            readConnectionParametersName(service, bleOperationQueue)
        }
    }

    override fun onDisconnect() = Unit

    override fun onCharacteristicsChanged(
        characteristic: BluetoothGattCharacteristic,
        newValue: ByteArray
    ) = Unit

    @androidx.annotation.RequiresPermission(android.Manifest.permission.BLUETOOTH_CONNECT)
    private fun readDeviceName(service: BluetoothGattService, bleOperationQueue: BleCommandQueue) {
        service.getCharacteristic(DEVICE_NAME_UUID)?.let { nameChar ->
            bleOperationQueue.enqueueOperation(Read(nameChar) { status, value ->
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    val newName = value.toString(Charsets.UTF_8)
                    Log.d(TAG, "Read Device name: $newName")
                    _deviceNameFlow.tryEmit(newName)
                }
            })
        }
    }

    @androidx.annotation.RequiresPermission(android.Manifest.permission.BLUETOOTH_CONNECT)
    private fun readConnectionParametersName(
        service: BluetoothGattService,
        bleOperationQueue: BleCommandQueue
    ) {
        service.getCharacteristic(DEVICE_PREFERRED_CONNECTION_PARAMS_UUID)?.let { nameChar ->
            bleOperationQueue.enqueueOperation(Read(nameChar) { status, value ->
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    val connectionParameters = PreferredConnectionParameters.parse(value)
                    Log.d(TAG, "Read connection parameters: $connectionParameters")
                    _preferredConnectionParametersFlow.tryEmit(connectionParameters)
                }
            })
        }
    }


    companion object {
        private const val TAG = "GenericAccessService"
        private const val DEFAULT_DEVICE_NAME = "Unknow"
        val SERVICE_UUID = UUID.fromString("00001800-0000-1000-8000-00805f9b34fb")!!
        val DEVICE_NAME_UUID = UUID.fromString("00002a00-0000-1000-8000-00805f9b34fb")!!
        val DEVICE_PREFERRED_CONNECTION_PARAMS_UUID =
            UUID.fromString("00002a04-0000-1000-8000-00805f9b34fb")!!
    }
}

data class PreferredConnectionParameters(
    val minimumConnectionInterval: UShort,
    val maximumConnectionInterval: UShort,
    val slaveLatency: UShort,
    val connectionSupervisionTimeoutMultiplier: UShort
) {

    companion object {

        @Suppress("MagicNumber")
        fun parse(rawData: ByteArray): PreferredConnectionParameters {
            //see https://github.com/oesmith/gatt-xml/blob/master/org.bluetooth.characteristic.gap.peripheral_preferred_connection_parameters.xml
            // Ensure there's enough data to parse
            require(rawData.size == 8){"rawData must contain at least 8 bytes"}

            val buffer = ByteBuffer.wrap(rawData)
            buffer.order(LITTLE_ENDIAN) // Set byte order to Little Endian

            val minInterval =
                ((buffer.short * 5) / 4).toUShort() // Reads 2 bytes and multiply by 1.25
            val maxInterval =
                ((buffer.short * 5) / 4).toUShort() // Reads next 2 bytes and multiply by 1.25
            val slaveLatency = buffer.short.toUShort()  // Reads next 2 bytes
            val connectionSupervisionTimeoutMultiplier =
                buffer.short.toUShort() // Reads next 2 bytes
            return PreferredConnectionParameters(
                minimumConnectionInterval = minInterval,
                maximumConnectionInterval = maxInterval,
                slaveLatency = slaveLatency,
                connectionSupervisionTimeoutMultiplier = connectionSupervisionTimeoutMultiplier
            )
        }
    }
}