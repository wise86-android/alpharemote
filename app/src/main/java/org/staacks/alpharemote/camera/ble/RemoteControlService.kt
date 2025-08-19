package org.staacks.alpharemote.camera.ble

import android.Manifest
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGatt.GATT_SUCCESS
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattService
import android.util.Log
import androidx.annotation.RequiresPermission
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.staacks.alpharemote.camera.ButtonCode
import org.staacks.alpharemote.camera.CAButton
import org.staacks.alpharemote.camera.CAJog
import org.staacks.alpharemote.camera.CameraActionStep
import org.staacks.alpharemote.camera.JogCode
import java.util.UUID
import kotlin.text.toHexString

class RemoteControlService : BleServiceManager {
    private lateinit var bleOperationQueue: BleCommandQueue
    private var commandCharacteristic: BluetoothGattCharacteristic? = null
    private val _deviceStatusFlow = MutableStateFlow(CameraStatus())
    val deviceStatus: StateFlow<CameraStatus> = _deviceStatusFlow.asStateFlow()

    enum class CommandStatus{
        Enqueue,
        Success,
        Fail
    }

    private val _lastCommandState = MutableStateFlow<CommandStatus>(CommandStatus.Enqueue)
    val commandStatus: StateFlow<CommandStatus> = _lastCommandState.asStateFlow()


    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    override fun onConnect(gatt: BluetoothGatt, bleCommandQueue: BleCommandQueue) {
        this.bleOperationQueue = bleCommandQueue
        val genericAccessService = gatt.getService(REMOTE_SERVICE_UUID)
        genericAccessService?.let { service ->
            enableStatusNotification(service)
        }
        commandCharacteristic = genericAccessService.getCharacteristic(COMMAND_CHARACTERISTIC_UUID)
    }

    override fun onDisconnect() {
        _deviceStatusFlow.tryEmit(CameraStatus())
    }

    @androidx.annotation.RequiresPermission(android.Manifest.permission.BLUETOOTH_CONNECT)
    private fun enableStatusNotification(service: BluetoothGattService){
        service.getCharacteristic(STATUS_CHARACTERISTIC_UUID)?.let { characteristic ->
            bleOperationQueue.enqueueOperation(SubscribeForUpdate(characteristic))
        }
    }

    override fun onCharacteristicsChanged(
        characteristic: BluetoothGattCharacteristic,
        newValue: ByteArray
    ){
        if(characteristic.uuid != STATUS_CHARACTERISTIC_UUID){
            return
        }
        _deviceStatusFlow.update { it.update(newValue) }
    }

    @androidx.annotation.RequiresPermission(android.Manifest.permission.BLUETOOTH_CONNECT)
    fun sendCommand(command: CameraActionStep){
        val payload = command.toCommand()
        if(payload.isEmpty())
            return
        commandCharacteristic?.let {

            bleOperationQueue.enqueueOperation(Write(it,payload, { status, _ ->
            if(status != GATT_SUCCESS){
                Log.e(TAG,"Failed to send command: $status")
                _lastCommandState.tryEmit(CommandStatus.Fail)
            }else{
                _lastCommandState.tryEmit(CommandStatus.Success)
            }
        }))
            _lastCommandState.tryEmit(CommandStatus.Enqueue)}
    }

    private fun CameraActionStep.toCommand():ByteArray{
        return when(this){
            is CAButton -> {
                byteArrayOf(0x01,this.toCode())
            }
            is CAJog -> {
                byteArrayOf(0x02,this.toCode(),this.pressedValue())
            }
            else -> byteArrayOf()
        }

    }

    private fun CAButton.toCode():Byte{
        return when(this.button){
            ButtonCode.SHUTTER_FULL -> if(pressed) SHUTTER_PRESSED else SHUTTER_RELEASED
            ButtonCode.SHUTTER_HALF -> if(pressed) SHUTTER_HALF_PRESSED else SHUTTER_HALF_RELEASED
            ButtonCode.RECORD -> if(pressed) RECORDING_PRESSED else RECORDING_RELEASED
            ButtonCode.AF_ON -> if(pressed) AUTO_FOCUS_PRESSED else AUTO_FOCUS_RELEASED
            ButtonCode.C1 -> if (pressed)  CUSTOM_BUTTON_PRESSED else CUSTOM_BUTTON_RELEASED
        }
    }

    private fun CAJog.toCode(): Byte{
        return when(this.jog){
            JogCode.ZOOM_IN -> if(pressed) ZOOM_IN_PRESSED else ZOOM_IN_RELEASED
            JogCode.ZOOM_OUT -> if(pressed) ZOOM_OUT_PRESSED else ZOOM_OUT_RElEASED
            JogCode.FOCUS_NEAR -> if(pressed) FOCUS_NEAR_PRESSED else FOCUS_NEAR_RELEASED
            JogCode.FOCUS_FAR -> if (pressed) FOCUS_FAR_PRESSED else FOCUS_FAR_RELEASED
        }
    }
    private fun CAJog.pressedValue(): Byte{
        return if(pressed)
            this.step
        else
            0x00
    }

    companion object{
        const val SHUTTER_HALF_RELEASED: Byte = 0x06.toByte()
        const val SHUTTER_HALF_PRESSED: Byte = 0x07.toByte()
        const val SHUTTER_PRESSED: Byte = 0x09.toByte()
        const val SHUTTER_RELEASED: Byte = 0x08.toByte()
        const val RECORDING_RELEASED: Byte = 0x0E.toByte()
        const val RECORDING_PRESSED: Byte = 0x0F.toByte()
        const val AUTO_FOCUS_RELEASED:Byte = 0x14.toByte()
        const val AUTO_FOCUS_PRESSED:Byte = 0x15.toByte()
        const val CUSTOM_BUTTON_RELEASED:Byte = 0x20.toByte()
        const val CUSTOM_BUTTON_PRESSED:Byte = 0x21.toByte()


        const val ZOOM_IN_RELEASED:Byte = 0x44.toByte()
        const val ZOOM_IN_PRESSED:Byte = 0x45.toByte()
        const val ZOOM_OUT_RElEASED:Byte = 0x46.toByte()
        const val ZOOM_OUT_PRESSED:Byte = 0x47.toByte()
        const val FOCUS_NEAR_RELEASED:Byte = 0x6A.toByte()
        const val FOCUS_NEAR_PRESSED:Byte = 0x6B.toByte()
        const val FOCUS_FAR_RELEASED:Byte = 0x6C.toByte()
        const val FOCUS_FAR_PRESSED:Byte = 0x6D.toByte()

        const val TAG = "RemoteControlService"
        val REMOTE_SERVICE_UUID = UUID.fromString("8000ff00-ff00-ffff-ffff-ffffffffffff")!!
        val COMMAND_CHARACTERISTIC_UUID = UUID.fromString("0000ff01-0000-1000-8000-00805f9b34fb")!!
        val STATUS_CHARACTERISTIC_UUID = UUID.fromString("0000ff02-0000-1000-8000-00805f9b34fb")!!
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

data class CameraStatus(val isRecording: Boolean = false,
                        val focus: FocusState=FocusState.LOST,
                        val shutter: ShutterState = ShutterState.RELEASED ){


    fun update(value: ByteArray): CameraStatus {
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