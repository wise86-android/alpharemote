package org.staacks.alpharemote.camera.ble

import android.Manifest
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattService
import android.location.Location
import android.util.Log
import androidx.annotation.RequiresPermission
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.time.ZoneOffset
import java.time.temporal.ChronoField

import java.util.Date
import java.util.TimeZone
import java.util.UUID
import kotlin.experimental.and
import kotlin.time.Duration.Companion.microseconds
import kotlin.time.Duration.Companion.minutes

class LocationService : BleServiceManager {

    enum class Status{
        Init,
        ReadingSettings,
        LocationUpdateEnabled,
        LocationUpdateDisabled
    }
    private lateinit var bleOperationQueue: BleCommandQueue
    private var writeLocationCharacteristics: BluetoothGattCharacteristic? = null
    private lateinit var locationGattService: BluetoothGattService
    private val _status = MutableStateFlow(Status.Init)
    val status: StateFlow<Status> = _status.asStateFlow()
    private lateinit var payloadSettings: PayloadSettings


    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    override fun onConnect(gatt: BluetoothGatt, bleCommandQueue: BleCommandQueue) {
        this.bleOperationQueue = bleCommandQueue
        val genericAccessService = gatt.getService(SERVICE_UUID)
        writeLocationCharacteristics = genericAccessService?.getCharacteristic(UPDATE_LOCATION)
        genericAccessService?.let { service ->
            locationGattService = service
            enableStatusNotification(service)
        }
    }

    override fun onDisconnect() {
        _status.update { Status.Init }
    }

    @RequiresPermission(android.Manifest.permission.BLUETOOTH_CONNECT)
    private fun enableStatusNotification(service: BluetoothGattService) {
        service.getCharacteristic(SERVICE_STATUS)?.let { characteristic ->
            _status.update { Status.ReadingSettings }
            bleOperationQueue.enqueueOperation(SubscribeForUpdate(characteristic))
        }
    }

    @RequiresPermission(android.Manifest.permission.BLUETOOTH_CONNECT)
    private fun acquireLock(service:BluetoothGattService){
        val lockChar = service.getCharacteristic(LOCK)
        if(lockChar!=null){
            Log.e(TAG,"acquire lock")

            bleOperationQueue.enqueueOperation(Write(lockChar,LOCK_MESSAGE,{status,_value ->
                if(status != BluetoothGatt.GATT_SUCCESS){
                    Log.e(TAG,"Failed to acquire lock")
                }
                service.getCharacteristic(LOCATION_INFO)?.let { locationInfo ->
                    bleOperationQueue.enqueueOperation(Write(locationInfo,LOCATION_INFO_ENABLE, { status, _value ->
                        Log.e(TAG,"read time correction")
                        service.getCharacteristic(TIME_CORRECTION_SETTINGS)?.let {
                            bleOperationQueue.enqueueOperation(Read(it,{status,value ->
                                Log.d(TAG,"Time correction settings: ${value.toHexString()}")
                            }))
                        }
                        Log.e(TAG,"area time correction")
                        service.getCharacteristic(AREA_ADJUST_SETTINGS)?.let {
                            bleOperationQueue.enqueueOperation(Read(it,{status,value ->
                                Log.d(TAG,"Area correction settings: ${value.toHexString()}")
                            }))
                        }
                        Log.e(TAG,"payload time correction")
                        service.getCharacteristic(PAYLOAD_SETTINGS)?.let {
                            bleOperationQueue.enqueueOperation(Read(it,{status,value ->
                                Log.d(TAG,"Payload settings: ${value.toHexString()}")
                                Log.d(TAG,"Payload settings: ${PayloadSettings(value)}")
                                this.payloadSettings = PayloadSettings(value)
                                _status.update { Status.LocationUpdateEnabled }
                            }))
                        }
                    }))
                }
            }))
        }else{
            service.getCharacteristic(PAYLOAD_SETTINGS)?.let {
                bleOperationQueue.enqueueOperation(Read(it,{status,value ->
                    Log.d(TAG,"Payload settings: ${value.toHexString()}")
                    this.payloadSettings = PayloadSettings(value)
                    _status.update { Status.LocationUpdateEnabled }
                }))
            }
        }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    override fun onCharacteristicsChanged(
        characteristic: BluetoothGattCharacteristic,
        newValue: ByteArray
    ){
        if(characteristic.uuid == SERVICE_STATUS){
            Log.d(TAG,"status update: ${newValue.toHexString()}")
            if(newValue.contentEquals(DISABLE_LOCATION_UPDATE)){
                Log.e(TAG,"Camera can not receive location update")
                _status.update { Status.LocationUpdateDisabled }
            }else if(newValue.contentEquals(ENABLE_LOCATION_UPDATE)){
                Log.e(TAG,"Camera is ready to receive location update")
                acquireLock(locationGattService)
            }
        }
    }

    @RequiresPermission(android.Manifest.permission.BLUETOOTH_CONNECT)
    fun updateLocationAndTime(location: Location,time: Date){
        if(_status.value != Status.LocationUpdateEnabled) {
            Log.e(TAG,"Location update not enabled, Skipped update")
            return
        }

        writeLocationCharacteristics?.let {
                val message = LocationInformationUpdateMessage(location, time)
                bleOperationQueue.enqueueOperation(
                    Write(
                        it, message.buildRawPayload(payloadSettings.shouldSendTimezoneAndDst),
                        { status, _value ->
                            if (status != BluetoothGatt.GATT_SUCCESS) {
                                Log.e(TAG, "Failed to update location: $location staus: $status")
                            }
                        })
                )
        }
    }


        companion object{
            private var TAG= LocationService::class.simpleName
            private val SERVICE_UUID = UUID.fromString("8000dd00-dd00-ffff-ffff-ffffffffffff")
            // 6600
            private val SERVICE_STATUS = UUID.fromString("0000dd01-0000-1000-8000-00805f9b34fb")

            val DISABLE_LOCATION_UPDATE = byteArrayOf(0x03,0x01,0x02,0x00)
            val ENABLE_LOCATION_UPDATE = byteArrayOf(0x03,0x01,0x02,0x01) // or 0x03 0x1 0x03 0x01 ?
            private val UPDATE_LOCATION = UUID.fromString("0000dd11-0000-1000-8000-00805f9b34fb")

            private val PAYLOAD_SETTINGS = UUID.fromString("0000dd21-0000-1000-8000-00805f9b34fb")
            // others
            private val LOCK = UUID.fromString("0000dd30-0000-1000-8000-00805f9b34fb")
            val LOCK_MESSAGE = byteArrayOf(0x01)
            val UNLOCK_MESSAGE = byteArrayOf(0x00)
            private val LOCATION_INFO = UUID.fromString("0000dd31-0000-1000-8000-00805f9b34fb")
            val LOCATION_INFO_ENABLE = byteArrayOf(0x01)
            val LOCATION_INFO_DISABLE = byteArrayOf(0x00)
            private val TIME_CORRECTION_SETTINGS = UUID.fromString("0000dd32-0000-1000-8000-00805f9b34fb")
            private val AREA_ADJUST_SETTINGS = UUID.fromString("0000dd33-0000-1000-8000-00805f9b34fb")

    }
}

private data class PayloadSettings(private val rawData:ByteArray){
    val isValid:Boolean
        get() = rawData.size == rawData[0].toInt() + 1

    val size:Byte
        get()= rawData[0]

    val shouldSendTimezoneAndDst :Boolean
        // true if second bit on byte 4 is 1
        get() = isValid && rawData[4] and 0x02.toByte() == 0x02.toByte()

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as PayloadSettings

        if (!rawData.contentEquals(other.rawData)) return false
        if (isValid != other.isValid) return false
        if (size != other.size) return false
        if (shouldSendTimezoneAndDst != other.shouldSendTimezoneAndDst) return false

        return true
    }

    override fun hashCode(): Int {
        var result = rawData.contentHashCode()
        result = 31 * result + isValid.hashCode()
        result = 31 * result + size
        result = 31 * result + shouldSendTimezoneAndDst.hashCode()
        return result
    }
}


@Suppress("MagicNumber")
private data class LocationInformationUpdateMessage(val location: Location,val time: Date){
    fun buildRawPayload(sendTimeZone: Boolean): ByteArray{
        if(sendTimeZone)
            return buildPayloadWithTimezone()
        else
            return buildPayloadWithoutTimezone()
    }


    private fun buildPayloadWithTimezone():ByteArray{
        val payload = ByteArray(95)
        payload[1] = 93 // probably first 2 bytes are the message length?
        // start of  magic payload data
        payload[2] = 0x08.toByte()
        payload[3] = 0x02.toByte()
        payload[4] = 0xFC.toByte() // -4
        payload[5] = 3.toByte()
        payload[6] = 0
        payload[7] = 0
        payload[8] = 0x10.toByte()
        payload[9] = 0x10.toByte()
        payload[10] = 0x10.toByte()
        //end of magic payload data
        appendLocation(payload,location)
        appendTime(payload,time)
        appendTimezone(payload,time)
        return payload
    }

    private fun buildPayloadWithoutTimezone():ByteArray{
        val payload = ByteArray(91)
        payload[1] = 89 // probably first 2 bytes are the message length?
        // start of  magic payload data
        payload[2] = 0x08.toByte()
        payload[3] = 0x02.toByte()
        payload[4] = 0xFC.toByte()
        payload[5] = 0x00
        payload[6] = 0x00
        payload[7] = 0x00
        payload[8] = 0x10.toByte()
        payload[9] = 0x10.toByte()
        payload[10] = 0x10.toByte()
        // end of  magic payload data
        appendLocation(payload,location)
        appendTime(payload,time)
        return payload
    }
    private fun appendLocation(payload:ByteArray,location: Location): ByteArray{
        val lat = (location.latitude*1.0E7).toInt()
        val lon = (location.longitude*1.0E7).toInt()
        Log.d("LocationService","lat: $lat, lon: $lon")
        lat.copyInto(payload,11)
        lon.copyInto(payload,15)

        return payload
    }

    private fun appendTime(payload:ByteArray,time: Date){
        val utc = time.toInstant().atZone(ZoneOffset.UTC)

        utc.get(ChronoField.YEAR).toShort().copyInto(payload,19)
        payload[21] = utc.get(ChronoField.MONTH_OF_YEAR).toByte()
        payload[22] = utc.get(ChronoField.DAY_OF_MONTH).toByte()
        payload[23] = utc.get(ChronoField.HOUR_OF_DAY).toByte()
        payload[24] = utc.get(ChronoField.MINUTE_OF_HOUR).toByte()
        payload[25] = utc.get(ChronoField.SECOND_OF_MINUTE).toByte()
    }

    private fun appendTimezone(payload:ByteArray,time: Date){
        val deviceTimeZone = TimeZone.getDefault()

        val minuteOffset = deviceTimeZone.rawOffset.microseconds.inWholeMinutes.toShort()
        minuteOffset.copyInto(payload,91)
        val daylightSavingMinute = if(deviceTimeZone.inDaylightTime(time)){
            deviceTimeZone.dstSavings.microseconds.inWholeMinutes.toShort()
        }else{
            0
        }
        daylightSavingMinute.copyInto(payload,93)
    }

    private fun Short.copyInto(array: ByteArray, startIndex: Int){
        val shortBuffer = ByteBuffer.allocate(2).order(ByteOrder.BIG_ENDIAN)
        shortBuffer.putShort(this)
        shortBuffer.array().copyInto(array, startIndex)
    }

    private fun Int.copyInto(array: ByteArray, startIndex: Int){
        val shortBuffer = ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN)
        shortBuffer.putInt(this)
        shortBuffer.array().copyInto(array, startIndex)
    }

}