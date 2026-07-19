package org.staacks.alpharemote.camera.ble

import android.Manifest
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattService
import android.location.Location
import android.util.Log
import androidx.annotation.RequiresPermission
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.time.ZoneOffset
import java.time.temporal.ChronoField

import java.util.Date
import java.util.TimeZone
import java.util.UUID
import kotlin.experimental.and
import kotlin.time.Duration.Companion.microseconds

class LocationService : BleServiceManager {

    enum class Status{
        Init,                   // camera location service not (yet) discovered
        CameraReady,            // camera reports it can receive location updates
        LocationUpdateDisabled, // camera reports it can not receive location updates
        LocationUpdateEnabled   // enable sequence completed, location may be pushed
    }
    private var bleOperationQueue: BleCommandQueue? = null
    private var writeLocationCharacteristics: BluetoothGattCharacteristic? = null
    private var locationGattService: BluetoothGattService? = null
    private val _status = MutableStateFlow(Status.Init)
    val status: StateFlow<Status> = _status.asStateFlow()
    private var payloadSettings: PayloadSettings? = null


    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    override fun onConnect(gatt: BluetoothGatt, bleCommandQueue: BleCommandQueue, scope: CoroutineScope) {
        this.bleOperationQueue = bleCommandQueue
        val service = gatt.getService(SERVICE_UUID)
        if (service == null) {
            Log.d(TAG, "Camera does not expose the location service.")
            return
        }
        locationGattService = service
        writeLocationCharacteristics = service.getCharacteristic(UPDATE_LOCATION)
        service.getCharacteristic(SERVICE_STATUS)?.let { characteristic ->
            scope.launch {
                bleCommandQueue.subscribe(characteristic)
            }
        }
    }

    override fun onDisconnect() {
        _status.update { Status.Init }
        payloadSettings = null
        writeLocationCharacteristics = null
        locationGattService = null
        bleOperationQueue = null
    }

    override fun onCharacteristicsChanged(
        characteristic: BluetoothGattCharacteristic,
        newValue: ByteArray
    ){
        if(characteristic.uuid != SERVICE_STATUS){
            return
        }
        Log.d(TAG,"status update: ${newValue.toHexString()}")
        if(newValue.contentEquals(DISABLE_LOCATION_UPDATE)){
            Log.d(TAG,"Camera can not receive location updates")
            _status.update { Status.LocationUpdateDisabled }
        }else if(newValue.contentEquals(ENABLE_LOCATION_UPDATE)){
            Log.d(TAG,"Camera is ready to receive location updates")
            // Do NOT run the enable sequence here. Occupying the camera's location function is
            // an explicit user choice (some cameras can not use the Bluetooth remote and the
            // location link at the same time), so it only happens when enableSync() is called.
            if (_status.value != Status.LocationUpdateEnabled) {
                _status.update { Status.CameraReady }
            }
        }
    }

    /**
     * Acquires the camera's location lock, enables its location-info link and reads the payload
     * settings. Must only be called when the user opted in to location sync.
     */
    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    suspend fun enableSync() {
        val service = locationGattService ?: return
        val queue = bleOperationQueue ?: return
        if (_status.value != Status.CameraReady) {
            Log.d(TAG, "enableSync skipped, camera not ready (${_status.value})")
            return
        }

        service.getCharacteristic(LOCK)?.let { lockCharacteristic ->
            Log.d(TAG, "Acquiring location lock")
            if (queue.write(lockCharacteristic, LOCK_MESSAGE) != BluetoothGatt.GATT_SUCCESS) {
                Log.e(TAG, "Failed to acquire lock")
            }
            service.getCharacteristic(LOCATION_INFO)?.let { locationInfo ->
                queue.write(locationInfo, LOCATION_INFO_ENABLE)
            }
            service.getCharacteristic(TIME_CORRECTION_SETTINGS)?.let {
                Log.d(TAG, "Time correction settings: ${queue.read(it).second.toHexString()}")
            }
            service.getCharacteristic(AREA_ADJUST_SETTINGS)?.let {
                Log.d(TAG, "Area correction settings: ${queue.read(it).second.toHexString()}")
            }
        }

        service.getCharacteristic(PAYLOAD_SETTINGS)?.let {
            val (readStatus, value) = queue.read(it)
            if (readStatus == BluetoothGatt.GATT_SUCCESS) {
                Log.d(TAG, "Payload settings: ${value.toHexString()}")
                payloadSettings = PayloadSettings(value)
                _status.update { Status.LocationUpdateEnabled }
            } else {
                Log.e(TAG, "Failed to read payload settings: $readStatus")
            }
        }
    }

    @RequiresPermission(android.Manifest.permission.BLUETOOTH_CONNECT)
    fun updateLocationAndTime(location: Location,time: Date){
        if(_status.value != Status.LocationUpdateEnabled) {
            Log.d(TAG,"Location update not enabled, skipped update")
            return
        }
        val queue = bleOperationQueue ?: return

        writeLocationCharacteristics?.let {
                val message = LocationInformationUpdateMessage(location, time)
                queue.enqueueOperation(
                    Write(
                        it, message.buildRawPayload(payloadSettings?.shouldSendTimezoneAndDst == true),
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
