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
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import org.staacks.alpharemote.MainActivity
import org.staacks.alpharemote.R
import org.staacks.alpharemote.data.SettingsStore
import org.staacks.alpharemote.camera.ButtonCode
import org.staacks.alpharemote.camera.CAButton
import org.staacks.alpharemote.camera.CACountdown
import org.staacks.alpharemote.camera.CAJog
import org.staacks.alpharemote.camera.CAWaitFor
import org.staacks.alpharemote.camera.CameraAction
import org.staacks.alpharemote.camera.CameraActionStep
import org.staacks.alpharemote.camera.CameraBLE
import org.staacks.alpharemote.camera.WaitTarget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
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
import org.staacks.alpharemote.camera.ble.BleConnectionState
import org.staacks.alpharemote.camera.ble.LocationService
import org.staacks.alpharemote.camera.ble.RemoteControlService
import org.staacks.alpharemote.utils.hasBluetoothPermission
import org.staacks.alpharemote.utils.hasLocationPermission
import java.util.LinkedList
import java.util.Timer
import java.util.TimerTask
import kotlin.concurrent.schedule
import kotlin.math.roundToLong
import kotlin.time.Duration.Companion.seconds

class AlphaRemoteService : Service() {
    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.IO + job)

    private var timer: TimerTask? = null
    private var notificationUI: NotificationUI? = null

    private val fusedLocationClient: FusedLocationProviderClient by lazy {
        LocationServices.getFusedLocationProviderClient(this)
    }

    private var hasConnectedDevice: Boolean = false

    private var cameraBLE: CameraBLE? = null

    private val locationRequest = LocationRequest.Builder(
        Priority.PRIORITY_HIGH_ACCURACY,
        20.seconds.inWholeMilliseconds
    ) // 20 seconds
        .setMinUpdateIntervalMillis(10.seconds.inWholeMilliseconds) // 10 seconds (minimum interval)
        .build()

    private val locationCallback: LocationCallback = object : LocationCallback() {

        override fun onLocationResult(locationResult: LocationResult) {
            val lastLocation = locationResult.lastLocation
            if (lastLocation !== null && hasBluetoothPermission(this@AlphaRemoteService))
                @SuppressLint("MissingPermission")
                cameraBLE?.setCameraLocation(lastLocation)
        }

    }

    private val _cameraState = MutableStateFlow<CameraState>(CameraState.Disconnected)
    val cameraState = _cameraState.asStateFlow()

    private fun startLocationUpdates() {
        if (hasLocationPermission(this)) {
            @SuppressLint("MissingPermission")
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                Looper.getMainLooper()
            )
            Log.d(TAG, "Location updates started.")
        } else {
            Log.d(TAG, "Location updates missing permission.")
        }

    }

    private fun stopLocationUpdates() {
        fusedLocationClient.removeLocationUpdates(locationCallback)
        Log.d(TAG, "Location updates stopped.")
    }

    companion object {
        private val TAG = AlphaRemoteService::class.java.name

        const val BUTTON_INTENT_ACTION = "NOTIFICATION_BUTTON"
        const val BUTTON_INTENT_CAMERA_ACTION_EXTRA = "camera_action"
        const val BUTTON_INTENT_CAMERA_ACTION_DOWN_EXTRA = "down"
        const val BUTTON_INTENT_CAMERA_ACTION_UP_EXTRA = "up"

        const val ADVANCED_SEQUENCE_INTENT_ACTION = "ADVANCED_SEQUENCE"
        const val ADVANCED_SEQUENCE_INTENT_BULB_DURATION_EXTRA = "duration"
        const val ADVANCED_SEQUENCE_INTENT_INTERVAL_DURATION_EXTRA = "interval"
        const val ADVANCED_SEQUENCE_INTENT_INTERVAL_COUNT_EXTRA = "count"

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

        private var pendingActionSteps = LinkedList<CameraActionStep>()
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
            if (intentDevice.address == cameraBLE?.deviceAddress && hasBluetoothPermission(context)) {
                @SuppressLint("MissingPermission")
                cameraBLE?.updateBondedState(context, newState)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()

        _cameraState.filterIsInstance(CameraState.Connected.Ready::class).onEach {
            cancelPendingActionSteps()
        }.launchIn(scope)

        _cameraState.onEach { notificationUI?.onCameraStateUpdate(it) }.launchIn(scope)

        registerReceiver(
            bondStateReceiver,
            IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(bondStateReceiver)
    }


    private fun onConnect() {
        Log.d(MainActivity.TAG, "onConnect")
        hasConnectedDevice = true
        notificationUI?.let {
            startForeground(
                it.notificationId,
                it.start(),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE or ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            )
        }
    }

    private fun onDisconnect() {
        Log.d(MainActivity.TAG, "onDisconnect")
        hasConnectedDevice = false
        stopLocationUpdates()
        cancelPendingActionSteps()
        stopForeground(STOP_FOREGROUND_REMOVE)
        notificationUI?.stop()
        cameraBLE = null
        job.cancelChildren()
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
            startCameraAction(cameraAction.getClickStepList(this))
        else if (translatedDown) //Button released
            startCameraAction(cameraAction.getPressStepList(this))
        else if (translatedUp) //Button pressed
            startCameraAction(cameraAction.getReleaseStepList())
    }

    private fun doDisconnectAction() {
        if (hasBluetoothPermission(this)) {
            @SuppressLint("MissingPermission")
            cameraBLE?.disconnectFromDevice()
        }
    }

    private fun doConnectAction(intent: Intent) {
        if (cameraBLE !== null) {
            Log.w(
                TAG,
                "onDeviceAppeared ignored as cameraBLE has already been instantiated."
            )
            return
        }
        val bleDevice = intent.getParcelableExtra(INTENT_EXTRA_DEVICE, BluetoothDevice::class.java)
        if (bleDevice == null) {
            Log.w(TAG, "INTENT_EXTRA_DEVICE is missing from the connecting intent")
            stopSelf()
            return
        }

        cancelPendingActionSteps()
        cameraBLE = CameraBLE(bleDevice).apply {
            collectCameraBleUpdates(this)
            @SuppressLint("MissingPermission")
            connectToDevice(this@AlphaRemoteService)
        }

        notificationUI = createNotificationUI()
    }

    private fun createNotificationUI(): NotificationUI {
        return notificationUI ?: NotificationUI(applicationContext).also { notificationUI = it }
    }

    private fun collectCameraBleUpdates(cameraBLE: CameraBLE) = with(cameraBLE) {
        connectionState.onEach {
            Log.d(TAG, "Connection state: $it")
            when (it) {
                BleConnectionState.Connected -> onConnect()
                BleConnectionState.Disconnected -> onDisconnect()
                else -> {}
            }
            notificationUI?.onCameraConnectionUpdate(it)
        }.launchIn(scope)

        locationUpdateStatus.onEach { newStatus ->
            when (newStatus) {
                LocationService.Status.LocationUpdateEnabled -> startLocationUpdates()
                LocationService.Status.LocationUpdateDisabled -> stopLocationUpdates()
                else -> {}
            }
        }.launchIn(scope)

        deviceName.onEach { newName ->
            _cameraState.update {
                when (it) {
                    is CameraState.Connected.Ready ->
                        it.copy(name = newName)

                    else -> CameraState.Connected.Ready(
                        name = newName,
                        deviceAddress,
                        focus = FocusState.LOST,
                        shutter = ShutterState.RELEASED,
                        recording = false
                    )
                }
            }
        }.launchIn(scope)

        deviceStatus.onEach { newStatus ->
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
                            deviceName.value,
                            deviceAddress,
                            focus = newStatus.focus,
                            shutter = newStatus.shutter,
                            recording = newStatus.isRecording,
                        )

                    else -> it
                }
            }
        }.launchIn(scope)

        remoteCommandStatus.onEach { newStatus ->
            if (newStatus == RemoteControlService.CommandStatus.Fail) {
                //The command failed. This is very likely a properly bonded camera with BLE remote setting disabled
                _cameraState.emit(CameraState.Connected.RemoteDisabled)
            }
        }.launchIn(scope)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(MainActivity.TAG, "onStartCommand: $intent")
        if (intent?.action !== CONNECT_INTENT_ACTION && !hasConnectedDevice)
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


            ADVANCED_SEQUENCE_INTENT_ACTION -> {
                val bulbDuration =
                    intent.getFloatExtra(ADVANCED_SEQUENCE_INTENT_BULB_DURATION_EXTRA, 0.0f)
                val intervalDuration =
                    intent.getFloatExtra(ADVANCED_SEQUENCE_INTENT_INTERVAL_DURATION_EXTRA, 0.0f)
                val intervalCount =
                    intent.getIntExtra(ADVANCED_SEQUENCE_INTENT_INTERVAL_COUNT_EXTRA, 0)

                val stepSequence: MutableList<CameraActionStep> = mutableListOf()
                stepSequence += CAButton(pressed = true, ButtonCode.SHUTTER_HALF)
                if (intervalDuration > 0) {
                    stepSequence += CACountdown(
                        getString(R.string.camera_advanced_interval_timer_label),
                        intervalDuration
                    )
                }
                stepSequence += CAButton(
                    pressed = true,
                    ButtonCode.SHUTTER_FULL,
                    isSequenceTrigger = true
                )
                if (bulbDuration > 0) {
                    stepSequence += CAWaitFor(WaitTarget.SHUTTER)
                }
                stepSequence += CAButton(pressed = false, ButtonCode.SHUTTER_FULL)
                stepSequence += CAButton(pressed = false, ButtonCode.SHUTTER_HALF)
                if (bulbDuration > 0) {
                    stepSequence += listOf(
                        CACountdown(
                            getString(R.string.camera_advanced_bulb_timer_label),
                            bulbDuration
                        ),
                        CAButton(pressed = true, ButtonCode.SHUTTER_HALF),
                        CAButton(pressed = true, ButtonCode.SHUTTER_FULL),
                        CAButton(pressed = false, ButtonCode.SHUTTER_FULL),
                        CAButton(pressed = false, ButtonCode.SHUTTER_HALF),
                    )
                }

                startCameraAction(
                    List(intervalCount) { stepSequence }.flatten()
                )
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
            SettingsStore(application).getCustomButtonList().let { customButtonList ->
                SettingsStore(application).getNotificationButtonSize()
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
        timer?.cancel()
        if (pendingActionSteps.isNotEmpty()) {
            pendingActionSteps.clear()
            for (button in ButtonCode.entries) {
                val action = CAButton(false, button)
                @SuppressLint("MissingPermission")
                cameraBLE?.executeCameraActionStep(action)
                _cameraState.update {
                    if (it is CameraState.Connected.Ready) {
                        it.applyCommand(action)
                    } else {
                        it
                    }
                }
            }
            pendingStepsCancelled = true
            updatePendingActionStatistics()
        }
        _cameraState.update {
            (it as? CameraState.Connected.Ready)?.copy(countdown = null, countdownLabel = null)
                ?: it
        }
        notificationUI?.hideCountdown()
        return pendingStepsCancelled
    }

    private fun isLongRunningSequence(steps: List<CameraActionStep>): Boolean {
        for (step in steps) {
            when (step) {
                is CACountdown -> return true
                is CAWaitFor -> return true
                else -> {}
            }
        }
        return false
    }

    @Synchronized
    fun startCameraAction(steps: List<CameraActionStep>) {
        if (cancelPendingActionSteps() && isLongRunningSequence(steps))
            return //If this is more than a simple button press and there were pending action, this button press is only used as a cancellation of the previous sequence
        pendingActionSteps.addAll(steps)
        executeNextCameraActionStep()
    }

    @Synchronized
    fun updatePendingActionStatistics() {
        var pendingTriggerCount = 0
        for (actionStep in pendingActionSteps) {
            if (actionStep is CAButton && actionStep.isSequenceTrigger)
                pendingTriggerCount++
        }
        _cameraState.update {
            (it as? CameraState.Connected.Ready)?.copy(pendingTriggerCount = pendingTriggerCount)
                ?: it
        }
    }

    @Synchronized
    fun executeNextCameraActionStep() {
        updatePendingActionStatistics()
        while ((pendingActionSteps.peek() is CAButton || pendingActionSteps.peek() is CAJog)) {
            pendingActionSteps.poll()?.let {
                updatePendingActionStatistics()
                @SuppressLint("MissingPermission")
                cameraBLE?.executeCameraActionStep(it)
            }
        }
        (pendingActionSteps.peek() as? CACountdown)?.let { step ->
            val time = (step.duration * 1000).roundToLong()
            timer?.cancel()
            timer = Timer().schedule(time) {
                countdownActionComplete()
            }
            val targetTime = SystemClock.elapsedRealtime() + time
            _cameraState.update {
                (it as? CameraState.Connected.Ready)?.copy(
                    countdown = targetTime,
                    countdownLabel = step.label
                )
                    ?: it
            }
            notificationUI?.showCountdown(targetTime, step.label)
            return
        }

        (_cameraState.value as? CameraState.Connected.Ready)?.let { cameraState ->
            checkWaitAction(cameraState)
        }

    }

    @Synchronized
    fun countdownActionComplete() {
        _cameraState.update {
            (it as? CameraState.Connected.Ready)?.copy(countdown = null, countdownLabel = null)
                ?: it
        }
        notificationUI?.hideCountdown()
        val nextAction = pendingActionSteps.peek()
        if (nextAction is CACountdown) {
            pendingActionSteps.removeFirst()
            executeNextCameraActionStep()
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

                WaitTarget.RECORDING -> if (state.shutter == ShutterState.PRESSED) {
                    pendingActionSteps.removeFirst()
                    executeNextCameraActionStep()
                }
            }
        }
    }

}