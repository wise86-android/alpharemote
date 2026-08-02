package org.staacks.alpharemote.core.ble

import android.bluetooth.BluetoothDevice
import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * The single GATT connection to the camera, shared by every feature that wants to talk to it.
 *
 * A GATT connection is one physical resource — two independent owners would race to bond,
 * discover services and hold the socket. This is what makes it safe for more than one feature to
 * touch BLE at once: each contributes a [BleServiceManager] and observes [state], and none of them
 * constructs or tears down [CameraBLE] itself.
 *
 * Two ways a manager can be attached, matching two different reasons a feature wants to be here:
 *
 * - [register] — for a manager that only *reacts* to a connection existing, without wanting to
 *   drive when one happens. It is attached once and stays attached across every future connection,
 *   for as long as the process runs.
 * - the `managers` handed to [connect] — for the feature that *drives* the connection lifecycle.
 *   These are supplied fresh on every call, exactly as [CameraBLE] itself is, so no manager's
 *   internal state (a cached device name, a pending command) survives into a reconnect that may be
 *   to a different camera.
 */
@OptIn(ExperimentalCoroutinesApi::class)
object CameraBleConnection {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val standingManagers = mutableListOf<BleServiceManager>()

    private val _connection = MutableStateFlow<CameraBLE?>(null)

    val state: StateFlow<BleConnectionState> = _connection
        .flatMapLatest { it?.connectionState ?: flowOf(BleConnectionState.Disconnected) }
        .stateIn(scope, SharingStarted.Eagerly, BleConnectionState.Disconnected)

    val deviceAddress: String?
        get() = _connection.value?.deviceAddress

    /**
     * Attaches a manager for the rest of the process's life.
     *
     * Idempotent by reference — call it every time you are in a position to (e.g. from a
     * component that is itself recreated across reconnects) rather than trying to arrange a
     * true call-once site. The one thing that must hold is that it runs before the first
     * [connect] that should see this manager.
     */
    fun register(manager: BleServiceManager) {
        if (manager !in standingManagers) {
            standingManagers += manager
            Log.d(TAG, "Registered ${manager::class.simpleName} (${standingManagers.size} standing)")
        }
    }

    /**
     * Starts one connection attempt, combining [managers] with whatever was attached via
     * [register].
     *
     * A no-op — returns `false` — if a connection is already active or in progress; the singleton
     * only ever owns one [CameraBLE] at a time. The caller decides what a no-op means for it (the
     * previous behaviour here was to log and drop the request).
     */
    @androidx.annotation.RequiresPermission(android.Manifest.permission.BLUETOOTH_CONNECT)
    fun connect(context: Context, device: BluetoothDevice, managers: List<BleServiceManager>): Boolean {
        if (_connection.value != null) {
            Log.w(TAG, "connect() ignored — a connection is already active")
            return false
        }

        Log.d(
            TAG,
            "connect() to ${device.address} with ${standingManagers.size} standing + " +
                "${managers.size} per-connection managers"
        )
        val connection = CameraBLE(device, standingManagers + managers)
        _connection.value = connection

        // The one place a stale CameraBLE could linger: once it reports itself Disconnected,
        // release it so the next connect() is not silently ignored as "already connected".
        scope.launch {
            connection.connectionState.first { it == BleConnectionState.Disconnected }
            if (_connection.value === connection) {
                Log.d(TAG, "Releasing the connection to ${device.address}")
                _connection.value = null
            }
        }

        connection.connectToDevice(context)
        return true
    }

    @androidx.annotation.RequiresPermission(android.Manifest.permission.BLUETOOTH_CONNECT)
    fun disconnect() {
        Log.d(TAG, "disconnect()")
        _connection.value?.disconnectFromDevice()
    }

    @androidx.annotation.RequiresPermission(android.Manifest.permission.BLUETOOTH_CONNECT)
    fun updateBondedState(context: Context, newState: Int) {
        _connection.value?.updateBondedState(context, newState)
    }

    private const val TAG = "CameraBleConnection"
}
