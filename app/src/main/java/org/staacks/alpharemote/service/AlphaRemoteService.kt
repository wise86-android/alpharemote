package org.staacks.alpharemote.service


import android.annotation.SuppressLint
import android.app.Service
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter

import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.os.Binder
import android.os.IBinder
import android.util.Log
import org.staacks.alpharemote.MainActivity
import org.staacks.alpharemote.R
import org.staacks.alpharemote.data.AppearanceSettings
import org.staacks.alpharemote.camera.ButtonCode
import org.staacks.alpharemote.camera.CAButton
import org.staacks.alpharemote.camera.CAJog
import org.staacks.alpharemote.camera.CAWaitFor
import org.staacks.alpharemote.camera.CameraAction
import org.staacks.alpharemote.camera.CameraActionStep
import org.staacks.alpharemote.camera.WaitTarget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.staacks.alpharemote.camera.CameraState
import org.staacks.alpharemote.camera.FocusState
import org.staacks.alpharemote.camera.ShutterState
import org.staacks.alpharemote.camera.ble.CameraControlStatusService
import org.staacks.alpharemote.camera.ble.LocationService
import org.staacks.alpharemote.camera.ble.RemoteControlService
import org.staacks.alpharemote.core.ble.BleConnectionState
import org.staacks.alpharemote.core.ble.CameraBleConnection
import org.staacks.alpharemote.core.ble.GenericAccessService
import org.staacks.alpharemote.feature.wificamera.data.ble.WifiHandover
import org.staacks.alpharemote.utils.hasBluetoothPermission
import org.staacks.alpharemote.utils.hasLocationPermission
import java.util.LinkedList

class AlphaRemoteService : Service() {
    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.IO + job)

    // Parent job of the collectors belonging to a single CameraBLE connection. Cancelled on
    // disconnect, unlike the service-lifetime collectors launched in onCreate.
    private var connectionJob: Job? = null

    private var notificationUI: NotificationUI? = null

    private val pendingActionSteps = LinkedList<CameraActionStep>()

    private val locationSyncController by lazy { LocationSyncController(this) }

    private var hasConnectedDevice: Boolean = false

    // The service instance for the current connection, fresh each time doConnectAction runs.
    // CameraBleConnection owns the GATT connection itself now; this is the one piece of it this
    // class still needs a direct reference to, for sendCameraActionStep.
    private var remoteControlService: RemoteControlService? = null

    private val _cameraState = MutableStateFlow<CameraState>(CameraState.Disconnected)
    val cameraState = _cameraState.asStateFlow()

    companion object {
        private val TAG = AlphaRemoteService::class.java.name

        const val BUTTON_INTENT_ACTION = "NOTIFICATION_BUTTON"
        const val BUTTON_INTENT_CAMERA_ACTION_EXTRA = "camera_action"
        const val BUTTON_INTENT_CAMERA_ACTION_DOWN_EXTRA = "down"
        const val BUTTON_INTENT_CAMERA_ACTION_UP_EXTRA = "up"

        const val DISCONNECT_INTENT_ACTION = "DEVICE_DISCONNECT"
        const val CONNECT_INTENT_ACTION = "DEVICE_CONNECT"
        const val INTENT_EXTRA_DEVICE = "BLE_DEVICE"
        fun sendDisconnectIntent(context: Context, device: BluetoothDevice? = null) {
            context.startService(
                Intent(context, AlphaRemoteService::class.java).apply {
                    action = DISCONNECT_INTENT_ACTION
                    putExtra(INTENT_EXTRA_DEVICE, device)
                }
            )
        }

        fun sendConnectIntent(context: Context, device: BluetoothDevice) {
            context.startForegroundService(
                Intent(context, AlphaRemoteService::class.java).apply {
                    action = CONNECT_INTENT_ACTION
                    putExtra(INTENT_EXTRA_DEVICE, device)
                }
            )
        }
    }

    private val bondStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            Log.d(TAG, "BLE received BluetoothDevice.ACTION_BOND_STATE_CHANGED.")
            val intentDevice =
                intent.getParcelableExtra(
                    BluetoothDevice.EXTRA_DEVICE,
                    BluetoothDevice::class.java
                )!!
            val newState =
                intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.BOND_NONE)
            Log.d(TAG, "Device changed bond state: $newState (address: ${intentDevice.address})")
            if (intentDevice.address == CameraBleConnection.deviceAddress &&
                hasBluetoothPermission(context)
            ) {
                @SuppressLint("MissingPermission")
                CameraBleConnection.updateBondedState(context, newState)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()

        // Leaving the ready state (disconnect, lost bond, remote disabled) invalidates any queued
        // steps. Ready-to-Ready updates must not cancel: a parked CAWaitFor is resumed by exactly
        // those status updates.
        _cameraState.onEach {
            if (it !is CameraState.Connected.Ready)
                cancelPendingActionSteps()
        }.launchIn(scope)

        _cameraState.onEach { notificationUI?.onCameraStateUpdate(it) }.launchIn(scope)

        // This service is recreated for every connection cycle (see onDisconnect's stopSelf), so
        // onCreate runs again each time — register() is idempotent by reference specifically so
        // that is safe. The Wi-Fi handover has no lifecycle of its own to drive this from; it only
        // needs to be present on whatever connection this service brings up.
        CameraBleConnection.register(WifiHandover.serviceManager())

        registerReceiver(
            bondStateReceiver,
            IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(bondStateReceiver)
        job.cancel()
    }


    private fun onConnect() {
        Log.d(MainActivity.TAG, "onConnect")
        hasConnectedDevice = true
    }

    private fun foregroundServiceTypes(): Int {
        // The location type may only be used if the location permission is granted, otherwise
        // startForeground throws a SecurityException.
        return if (hasLocationPermission(this))
            ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE or ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
        else
            ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
    }

    private fun onDisconnect() {
        Log.d(MainActivity.TAG, "onDisconnect")
        hasConnectedDevice = false
        locationSyncController.stop()
        cancelPendingActionSteps()
        stopForeground(STOP_FOREGROUND_REMOVE)
        notificationUI?.stop()
        // NotificationUI cannot be restarted after stop() (its scope is cancelled), so drop it
        // and create a fresh instance on the next connection.
        notificationUI = null
        remoteControlService = null
        // Cancel only the collectors of this connection. While the service is bound (e.g. by
        // AlphaRemoteRepository) it survives stopSelf() and onCreate will not run again, so the
        // service-lifetime collectors launched there must stay active for the next connection.
        connectionJob?.cancel()
        connectionJob = null
        _cameraState.update { CameraState.Disconnected }
        stopSelf()
    }

    internal fun executeCameraAction(cameraAction: CameraAction, down: Boolean, up: Boolean) {
        var translatedUp = up
        var translatedDown = down

        // Translate toggle release to down or up depending on button state
        if (cameraAction.toggle) {
            translatedDown =
                false // Toggle only acts on button release. Do not pass through down events
            if (translatedUp) {
                (_cameraState.value as? CameraState.Connected.Ready)?.let { cameraState ->
                    translatedUp =
                        cameraAction.preset.template.referenceButton in cameraState.pressedButtons
                    translatedDown = !translatedUp
                }
            }
        }

        if (translatedDown && translatedUp) //Simple click, i.e. button in notification area
            startCameraAction(cameraAction.getClickStepList())
        else if (translatedDown) //Button released
            startCameraAction(cameraAction.getPressStepList())
        else if (translatedUp) //Button pressed
            startCameraAction(cameraAction.getReleaseStepList())
    }

    /**
     * Sends one step to the camera, replacing what `CameraBLE.executeCameraActionStep` used to do
     * directly — the connected-state gate that method applied before delegating to the remote
     * control service now lives here, alongside the service reference itself.
     */
    @SuppressLint("MissingPermission")
    private fun sendCameraActionStep(step: CameraActionStep) {
        if (CameraBleConnection.state.value == BleConnectionState.Connected) {
            remoteControlService?.sendCommand(step)
        }
    }

    private fun doDisconnectAction() {
        if (hasBluetoothPermission(this)) {
            @SuppressLint("MissingPermission")
            CameraBleConnection.disconnect()
        }
    }

    private fun doConnectAction(intent: Intent) {
        val bleDevice = intent.getParcelableExtra(INTENT_EXTRA_DEVICE, BluetoothDevice::class.java)
        if (bleDevice == null) {
            Log.w(TAG, "INTENT_EXTRA_DEVICE is missing from the connecting intent")
            stopSelf()
            return
        }

        cancelPendingActionSteps()

        // The service was started with startForegroundService, which requires startForeground
        // to be called within a few seconds. Bonding and connecting to the camera can take much
        // longer (especially on first pairing), so go foreground immediately with a
        // "connecting" notification instead of waiting for the GATT connection.
        createNotificationUI().let {
            startForeground(it.notificationId, it.start(), foregroundServiceTypes())
        }

        // Fresh per connection, same as CameraBLE itself: reused instances would carry state
        // (e.g. GenericAccessService's last-known device name) across a reconnect to a possibly
        // different, or freshly reset, camera.
        val genericAccessService = GenericAccessService()
        val locationService = LocationService()
        val newRemoteControlService = RemoteControlService()
        val cameraControlStatusService = CameraControlStatusService()
        remoteControlService = newRemoteControlService

        collectCameraBleUpdates(
            genericAccessService,
            newRemoteControlService,
            locationService,
            cameraControlStatusService
        )

        @SuppressLint("MissingPermission")
        val started = CameraBleConnection.connect(
            this,
            bleDevice,
            listOf(genericAccessService, locationService, newRemoteControlService, cameraControlStatusService)
        )
        if (!started) {
            Log.w(TAG, "onDeviceAppeared ignored — a connection is already active.")
        }
    }

    private fun createNotificationUI(): NotificationUI {
        return notificationUI ?: NotificationUI(applicationContext).also { notificationUI = it }
    }

    /**
     * Wires up the reactive plumbing for one connection attempt.
     *
     * The flows themselves ([CameraBleConnection.state], the two services' own state) now outlive
     * any single [android.bluetooth.BluetoothGatt] connection, but this is still called fresh from
     * [doConnectAction] each time: [genericAccessService] and [remoteControlService] are new
     * instances per attempt, so their flows are too, and the previous attempt's collectors were
     * already torn down in [onDisconnect].
     */
    private fun collectCameraBleUpdates(
        genericAccessService: GenericAccessService,
        remoteControlService: RemoteControlService,
        locationService: LocationService,
        cameraControlStatusService: CameraControlStatusService
    ) {
        connectionJob?.cancel()
        val connectionScope =
            CoroutineScope(Dispatchers.IO + SupervisorJob(job).also { connectionJob = it })

        CameraBleConnection.state.onEach {
            Log.d(TAG, "Connection state: $it")
            when (it) {
                BleConnectionState.Connected -> onConnect()
                BleConnectionState.Disconnected -> onDisconnect()
                else -> {}
            }
            notificationUI?.onCameraConnectionUpdate(it)
        }.launchIn(connectionScope)

        // GPS push to the camera is handled by the controller, gated behind the user setting.
        locationSyncController.start(locationService, connectionScope)

        genericAccessService.deviceName.onEach { newName ->
            _cameraState.update {
                when (it) {
                    is CameraState.Connected.Ready ->
                        it.copy(name = newName)

                    // CC09 may have already reported the remote as disabled by the time the
                    // device name arrives (the two are independent, racing reads). RemoteDisabled
                    // carries no name to update, so leave it alone rather than overwrite it with
                    // an optimistic Ready.
                    is CameraState.Connected.RemoteDisabled -> it

                    else -> CameraState.Connected.Ready(
                        name = newName,
                        CameraBleConnection.deviceAddress.orEmpty(),
                        focus = FocusState.LOST,
                        shutter = ShutterState.RELEASED,
                        recording = false
                    )
                }
            }
        }.launchIn(connectionScope)

        // The authoritative, live source for "is the camera's own Bluetooth remote control
        // setting on" (PROTOCOL.md §6.1's CC09 RemoteControlAvailable) — pushed as soon as the
        // camera knows, rather than only discovered reactively from a failed command write below.
        cameraControlStatusService.remoteControlAvailable.onEach { available ->
            when (available) {
                false -> _cameraState.update { CameraState.Connected.RemoteDisabled }
                true -> _cameraState.update {
                    if (it is CameraState.Connected.RemoteDisabled) {
                        val status = remoteControlService.deviceStatus.value
                        CameraState.Connected.Ready(
                            genericAccessService.deviceName.value,
                            CameraBleConnection.deviceAddress.orEmpty(),
                            focus = status.focus,
                            shutter = status.shutter,
                            recording = status.isRecording,
                        )
                    } else it
                }
                null -> {} // Not yet known.
            }
        }.launchIn(connectionScope)

        remoteControlService.deviceStatus.onEach { newStatus ->
            Log.d(TAG, "New status: $newStatus")
            _cameraState.update {
                when (it) {
                    is CameraState.Connected.Ready -> it.copy(
                        focus = newStatus.focus,
                        shutter = newStatus.shutter,
                        recording = newStatus.isRecording
                    )

                    is CameraState.Connected.RemoteDisabled ->
                        CameraState.Connected.Ready(
                            genericAccessService.deviceName.value,
                            CameraBleConnection.deviceAddress.orEmpty(),
                            focus = newStatus.focus,
                            shutter = newStatus.shutter,
                            recording = newStatus.isRecording,
                        )

                    else -> it
                }
            }
            // A parked CAWaitFor resumes on the status notification it was waiting for.
            (_cameraState.value as? CameraState.Connected.Ready)?.let { checkWaitAction(it) }
        }.launchIn(connectionScope)

        // Fallback in case CC09 above didn't catch it (e.g. an older camera that doesn't expose
        // that characteristic): a failed command write also means the remote setting is off.
        remoteControlService.commandStatus.onEach { newStatus ->
            if (newStatus == RemoteControlService.CommandStatus.Fail) {
                //The command failed. This is very likely a properly bonded camera with BLE remote setting disabled
                _cameraState.emit(CameraState.Connected.RemoteDisabled)
            }
        }.launchIn(connectionScope)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(MainActivity.TAG, "onStartCommand: $intent")
        // Note: structural comparison (!=) is required here. The intent is re-parceled by the
        // system, so its action string is a different instance than the constant.
        if (intent?.action != CONNECT_INTENT_ACTION &&
            intent?.action != DISCONNECT_INTENT_ACTION &&
            !hasConnectedDevice
        )
            return START_NOT_STICKY

        when (intent?.action) {
            CONNECT_INTENT_ACTION -> doConnectAction(intent)
            DISCONNECT_INTENT_ACTION -> doDisconnectAction()
            BUTTON_INTENT_ACTION -> {
                val cameraAction =
                    intent.getSerializableExtra(
                        BUTTON_INTENT_CAMERA_ACTION_EXTRA,
                        CameraAction::class.java
                    ) ?: return@onStartCommand START_NOT_STICKY
                val down = intent.getBooleanExtra(BUTTON_INTENT_CAMERA_ACTION_DOWN_EXTRA, true)
                val up = intent.getBooleanExtra(BUTTON_INTENT_CAMERA_ACTION_UP_EXTRA, true)

                executeCameraAction(cameraAction, down, up)
            }
        }

        return START_NOT_STICKY
    }

    inner class LocalBinder : Binder() {
        fun getService(): AlphaRemoteService = this@AlphaRemoteService
    }

    private val binder = LocalBinder()

    override fun onBind(p0: Intent?): IBinder {
        return binder
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        scope.launch {
            val appearanceSettings = AppearanceSettings(application)
            appearanceSettings.getCustomButtonList().let { customButtonList ->
                appearanceSettings.getNotificationButtonSize()
                    ?.let { notificationButtonSize ->
                        notificationUI?.updateCustomButtons(
                            customButtonList,
                            notificationButtonSize
                        )
                    }
            }
        }
    }

    @Synchronized
    fun cancelPendingActionSteps(): Boolean {
        var pendingStepsCancelled = false
        if (pendingActionSteps.isNotEmpty()) {
            pendingActionSteps.clear()
            for (button in ButtonCode.entries) {
                val action = CAButton(false, button)
                sendCameraActionStep(action)
                _cameraState.update {
                    if (it is CameraState.Connected.Ready) {
                        it.applyCommand(action)
                    } else {
                        it
                    }
                }
            }
            pendingStepsCancelled = true
        }
        return pendingStepsCancelled
    }

    private fun isLongRunningSequence(steps: List<CameraActionStep>): Boolean {
        return steps.any { it is CAWaitFor }
    }

    @Synchronized
    fun startCameraAction(steps: List<CameraActionStep>) {
        if (cancelPendingActionSteps() && isLongRunningSequence(steps))
            return //If this is more than a simple button press and there were pending action, this button press is only used as a cancellation of the previous sequence
        pendingActionSteps.addAll(steps)
        executeNextCameraActionStep()
    }

    @Synchronized
    fun executeNextCameraActionStep() {
        while ((pendingActionSteps.peek() is CAButton || pendingActionSteps.peek() is CAJog)) {
            pendingActionSteps.poll()?.let { sendCameraActionStep(it) }
        }

        // Anything left at the head is a CAWaitFor. It may already be satisfied by the current
        // state, otherwise the queue parks here until the next status update.
        (_cameraState.value as? CameraState.Connected.Ready)?.let { cameraState ->
            checkWaitAction(cameraState)
        }
    }

    @Synchronized
    fun checkWaitAction(state: CameraState.Connected.Ready) {
        val nextAction = pendingActionSteps.peek()
        if (nextAction is CAWaitFor) {
            when (nextAction.target) {
                WaitTarget.FOCUS -> if (state.focus == FocusState.ACQUIRED) {
                    pendingActionSteps.removeFirst()
                    executeNextCameraActionStep()
                }

                WaitTarget.SHUTTER -> if (state.shutter == ShutterState.PRESSED) {
                    pendingActionSteps.removeFirst()
                    executeNextCameraActionStep()
                }

                WaitTarget.RECORDING -> if (state.recording) {
                    pendingActionSteps.removeFirst()
                    executeNextCameraActionStep()
                }
            }
        }
    }

}