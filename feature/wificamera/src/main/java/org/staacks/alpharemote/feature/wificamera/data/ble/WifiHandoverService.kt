package org.staacks.alpharemote.feature.wificamera.data.ble

import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattService
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import org.staacks.alpharemote.core.ble.BleCommandQueue
import org.staacks.alpharemote.core.ble.BleServiceManager
import org.staacks.alpharemote.feature.wificamera.domain.WifiCredentials
import java.util.UUID

/**
 * Whether the camera's Wi-Fi handover characteristics are reachable right now.
 *
 * Tracks the BLE connection this manager is attached to, not the Wi-Fi connection that comes out
 * of using it — those are two different links, and this only speaks to the first.
 */
enum class WifiHandoverAvailability {
    UNAVAILABLE,
    READY
}

/**
 * The camera's Wi-Fi handover, over its `8000CC00` BLE service (PROTOCOL.md §6).
 *
 * A [BleServiceManager] like any other, but registered with
 * [org.staacks.alpharemote.core.ble.CameraBleConnection] as a *standing* manager rather than a
 * per-connection one (see that object's doc comment): this feature does not drive when the camera
 * is BLE-connected — that is the remote-control feature's job — it only reacts to a connection
 * existing whenever one does, so it has to stay attached across every connect/disconnect cycle
 * that feature drives.
 *
 * Sequence, once connected:
 * 1. Subscribe to the Wi-Fi launch status characteristic (`CC05`).
 * 2. Write `01` to the Wi-Fi-on characteristic (`CC08`).
 * 3. Wait for a `CC05` notification; byte 3 of the payload is `1` on success.
 * 4. Read the SSID (`CC06`) and password (`CC07`) characteristics, both ASCII starting at byte 3.
 *
 * Characteristics are matched by UUID **prefix**, not exact value — PROTOCOL.md §6 flags this
 * explicitly, presumably because the suffix Sony's real hardware uses for this service does not
 * follow the Bluetooth SIG base UUID pattern the way a 16-bit "official" characteristic normally
 * would. Matching on the documented 8-hex-digit prefix is the robust reading of that note.
 */
class WifiHandoverService : BleServiceManager {

    private var bleCommandQueue: BleCommandQueue? = null
    private var wifiLaunchStatusCharacteristic: BluetoothGattCharacteristic? = null
    private var ssidCharacteristic: BluetoothGattCharacteristic? = null
    private var passwordCharacteristic: BluetoothGattCharacteristic? = null
    private var wifiOnCharacteristic: BluetoothGattCharacteristic? = null

    private val _availability = MutableStateFlow(WifiHandoverAvailability.UNAVAILABLE)
    val availability: StateFlow<WifiHandoverAvailability> = _availability.asStateFlow()

    /**
     * Bridges the `CC05` notification — delivered through the synchronous
     * [onCharacteristicsChanged] callback — into something [activateWifi] can suspend on.
     * Buffered by one: the notification can arrive before [activateWifi] starts collecting if the
     * write and the notification race, and the buffered value is exactly the one we are about to
     * ask for anyway.
     */
    private val wifiLaunchStatus = MutableSharedFlow<ByteArray>(extraBufferCapacity = 1)

    @androidx.annotation.RequiresPermission(android.Manifest.permission.BLUETOOTH_CONNECT)
    override fun onConnect(gatt: BluetoothGatt, bleCommandQueue: BleCommandQueue, scope: CoroutineScope) {
        val service = gatt.getService(SERVICE_UUID)
        if (service == null) {
            Log.d(TAG, "Camera does not offer the Wi-Fi handover service")
            _availability.value = WifiHandoverAvailability.UNAVAILABLE
            return
        }

        val launchStatus = service.characteristicByUuidPrefix(WIFI_LAUNCH_STATUS_PREFIX)
        val ssid = service.characteristicByUuidPrefix(SSID_PREFIX)
        val password = service.characteristicByUuidPrefix(PASSWORD_PREFIX)
        val wifiOn = service.characteristicByUuidPrefix(WIFI_ON_PREFIX)

        if (launchStatus == null || ssid == null || password == null || wifiOn == null) {
            Log.w(TAG, "Wi-Fi handover service is missing an expected characteristic")
            _availability.value = WifiHandoverAvailability.UNAVAILABLE
            return
        }

        this.bleCommandQueue = bleCommandQueue
        wifiLaunchStatusCharacteristic = launchStatus
        ssidCharacteristic = ssid
        passwordCharacteristic = password
        wifiOnCharacteristic = wifiOn

        // Subscribed once, up front, rather than inside activateWifi(): a subscription only
        // needs to happen once per connection, and doing it here means availability genuinely
        // means "ready to call activateWifi()" rather than "ready once one more round trip
        // happens".
        scope.launch {
            val status = bleCommandQueue.subscribe(launchStatus)
            if (status != BluetoothGatt.GATT_SUCCESS) {
                Log.w(TAG, "Could not subscribe to the Wi-Fi launch status characteristic: $status")
                _availability.value = WifiHandoverAvailability.UNAVAILABLE
                return@launch
            }
            _availability.value = WifiHandoverAvailability.READY
        }
    }

    override fun onDisconnect() {
        bleCommandQueue = null
        wifiLaunchStatusCharacteristic = null
        ssidCharacteristic = null
        passwordCharacteristic = null
        wifiOnCharacteristic = null
        _availability.value = WifiHandoverAvailability.UNAVAILABLE
    }

    override fun onCharacteristicsChanged(
        characteristic: BluetoothGattCharacteristic,
        newValue: ByteArray
    ) {
        if (characteristic.uuid == wifiLaunchStatusCharacteristic?.uuid) {
            wifiLaunchStatus.tryEmit(newValue)
        }
    }

    /**
     * Turns the camera's Wi-Fi on and reads the credentials to join it.
     *
     * Every call re-reads both characteristics rather than caching a previous result: the
     * password is only stable until the owner resets the camera's network settings, and the
     * point of doing this over BLE at all is to never need a cached value.
     */
    @androidx.annotation.RequiresPermission(android.Manifest.permission.BLUETOOTH_CONNECT)
    suspend fun activateWifi(): Result<WifiCredentials> {
        val queue = bleCommandQueue
        val wifiOn = wifiOnCharacteristic
        val ssid = ssidCharacteristic
        val password = passwordCharacteristic
        if (queue == null || wifiOn == null || ssid == null || password == null) {
            return Result.failure(IllegalStateException("No camera connected over BLE"))
        }

        return runCatching {
            Log.d(TAG, "Turning the camera's Wi-Fi on")
            queue.write(wifiOn, byteArrayOf(0x01))

            val launchStatus = withTimeout(WIFI_LAUNCH_TIMEOUT_MS) { wifiLaunchStatus.first() }
            if (!WifiHandoverParsing.launchSucceeded(launchStatus)) {
                val reason = WifiHandoverParsing.failureReason(launchStatus)
                throw IllegalStateException("Camera did not turn its Wi-Fi on (reason=$reason)")
            }

            val (ssidStatus, ssidValue) = queue.read(ssid)
            val (passwordStatus, passwordValue) = queue.read(password)
            if (ssidStatus != BluetoothGatt.GATT_SUCCESS || passwordStatus != BluetoothGatt.GATT_SUCCESS) {
                throw IllegalStateException(
                    "Could not read Wi-Fi credentials (ssid=$ssidStatus, password=$passwordStatus)"
                )
            }

            WifiCredentials(
                ssid = WifiHandoverParsing.asciiFromByteThree(ssidValue),
                password = WifiHandoverParsing.asciiFromByteThree(passwordValue)
            )
        }.onFailure { Log.w(TAG, "Wi-Fi handover failed", it) }
    }

    private fun BluetoothGattService.characteristicByUuidPrefix(
        prefix: String
    ): BluetoothGattCharacteristic? =
        characteristics.firstOrNull { it.uuid.toString().startsWith(prefix, ignoreCase = true) }

    companion object {
        private const val TAG = "WifiHandoverService"

        val SERVICE_UUID: UUID = UUID.fromString("8000CC00-CC00-FFFF-FFFF-FFFFFFFFFFFF")

        private const val WIFI_LAUNCH_STATUS_PREFIX = "0000cc05"
        private const val SSID_PREFIX = "0000cc06"
        private const val PASSWORD_PREFIX = "0000cc07"
        private const val WIFI_ON_PREFIX = "0000cc08"

        private const val WIFI_LAUNCH_TIMEOUT_MS = 15_000L
    }
}
