package org.staacks.alpharemote.camera.ble

import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattService
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.UUID
import kotlin.text.toHexString

class RemoteControlService {
    private val bleOperationQueue: BleCommandQueue

    private val _deviceStatusFlow = MutableStateFlow(CameraStatus2())
    val deviceStatus: StateFlow<CameraStatus2> = _deviceStatusFlow.asStateFlow()

    constructor(bleOperationQueue: BleCommandQueue) {
        this.bleOperationQueue=bleOperationQueue
    }

    @androidx.annotation.RequiresPermission(android.Manifest.permission.BLUETOOTH_CONNECT)
    fun attach(gatt: BluetoothGatt){
        val genericAccessService = gatt.getService(REMOTE_SERVICE_UUID)
        genericAccessService?.let { service ->
            enableStatusNotification(service)
        }
    }

    @androidx.annotation.RequiresPermission(android.Manifest.permission.BLUETOOTH_CONNECT)
    private fun enableStatusNotification(service: BluetoothGattService){
        service.getCharacteristic(STATUS_CHARACTERISTIC_UUID)?.let { characteristic ->
            bleOperationQueue.enqueueOperation(SubscribeForUpdate(characteristic))
        }
    }

    fun onCharacteristicChanged(characteristic: BluetoothGattCharacteristic,newValue:ByteArray){
        if(characteristic.uuid != STATUS_CHARACTERISTIC_UUID){
            return
        }
        _deviceStatusFlow.update { it.update(newValue) }
    }

    companion object{
        const val TAG = "RemoteControlService"
        val REMOTE_SERVICE_UUID = UUID.fromString("8000ff00-ff00-ffff-ffff-ffffffffffff")!!
        val COMMAND_CHARACTERISTIC_UUID = UUID.fromString("0000ff01-0000-1000-8000-00805f9b34fb")!!
        val STATUS_CHARACTERISTIC_UUID = UUID.fromString("0000ff02-0000-1000-8000-00805f9b34fb")!!
    }
}


enum class ButtonStatus{
    PRESSED,
    HALF_PRESSED,
    RELEASED,
    HALF_RELEASED
}
data class CameraStatus(val autoFocus: ButtonStatus = ButtonStatus.RELEASED,
                        val shutter: ButtonStatus = ButtonStatus.RELEASED,
                        val recording: ButtonStatus = ButtonStatus.RELEASED,
                        val customButton: ButtonStatus = ButtonStatus.RELEASED){

    companion object{
        fun parse(value: ByteArray): CameraStatus{
            Log.d("CameraStatus","reviced: ${value.joinToString(" ")}")
            val length = value[0]

            return when (value[1].toInt()) {
                0x06 -> CameraStatus(shutter = ButtonStatus.HALF_PRESSED)
                0x07 -> CameraStatus(shutter = ButtonStatus.HALF_RELEASED)
                0x08 -> CameraStatus(shutter = ButtonStatus.RELEASED)
                0x09 -> CameraStatus(shutter = ButtonStatus.PRESSED)
                0x0e -> CameraStatus(recording = ButtonStatus.RELEASED)
                0x0f -> CameraStatus(recording = ButtonStatus.PRESSED)
                0x14 -> CameraStatus(autoFocus = ButtonStatus.RELEASED)
                0x15 -> CameraStatus(autoFocus = ButtonStatus.PRESSED)
                0x20 -> CameraStatus(customButton = ButtonStatus.RELEASED)
                0x21 -> CameraStatus(customButton = ButtonStatus.PRESSED)
                else -> CameraStatus()
            }
        }
    }
}

enum class FocusState{
    LOST,
    ACQUIRED,
    SEARCHING,
}

enum class ShutterState{
    PRESSED,
    RELEASED
}

data class CameraStatus2(val isRecording: Boolean = false,val focus: FocusState=FocusState.LOST,val shutter: ShutterState = ShutterState.RELEASED ){


    fun update(value: ByteArray): CameraStatus2 {
        val length = value[0]
        if (length != 2.toByte()) {
            Log.i(RemoteControlService.TAG, "Invalid update package: ${value.toHexString()}")
            return this
        }
        val button = value[1]
        val status = value[2]
        return when (button) {
            BUTTON_CODE_RECORDING -> this.copy(isRecording = status.isRecording())
            BUTTON_CODE_FOCUS -> this.copy(focus = status.toFocusState())
            BUTTON_CODE_SHUTTER -> this.copy(shutter = status.toShutterState())
            else -> {
                Log.i(RemoteControlService.TAG, "Unknown button: rawValue:${value.toHexString()}")
                this
            }
        }
    }

    companion object {

        private const val BUTTON_CODE_RECORDING: Byte = 0xD5.toByte()
        private const val BUTTON_CODE_FOCUS: Byte = 0x3F.toByte()
        private const val BUTTON_CODE_SHUTTER: Byte = 0xA0.toByte()

        private const val STATUS_RECORDING_ON: Byte = 0x20.toByte()
        private const val STATUS_RECORDING_OFF: Byte = 0x00.toByte()

        private const val STATUS_FOCUS_LOST: Byte = 0x00.toByte()
        private const val STATUS_FOCUS_ACQUIRED: Byte = 0x20.toByte()
        private const val STATUS_FOCUS_SEARCHING: Byte = 0x40.toByte()

        private const val STATUS_SHUTTER_RELEASED: Byte = 0x00.toByte()
        private const val STATUS_SHUTTER_PRESSED: Byte = 0x20.toByte()


        fun Byte.isRecording(): Boolean {
            return when(this){
                STATUS_RECORDING_ON -> true
                STATUS_RECORDING_OFF -> false
                else -> {
                    Log.i(
                        RemoteControlService.TAG,
                        "Unknown recording state:${this.toHexString()}"
                    )
                    false
                }
            }
        }

        fun Byte.toFocusState(): FocusState {
            return when (this) {
                STATUS_FOCUS_LOST -> FocusState.LOST
                STATUS_FOCUS_ACQUIRED -> FocusState.ACQUIRED
                STATUS_FOCUS_SEARCHING -> FocusState.SEARCHING
                else -> {
                    Log.i(
                        RemoteControlService.TAG,
                        "Unknown focus state:${this.toHexString()}"
                    )
                    FocusState.LOST
                }
            }
        }

        fun Byte.toShutterState(): ShutterState {
            return when (this) {
                STATUS_SHUTTER_RELEASED -> ShutterState.RELEASED
                STATUS_SHUTTER_PRESSED -> ShutterState.PRESSED
                else -> {
                    Log.i(
                        RemoteControlService.TAG,
                        "Unknown shutter state:${this.toHexString()}"
                    )
                    ShutterState.RELEASED
                }
            }
        }
    }
}