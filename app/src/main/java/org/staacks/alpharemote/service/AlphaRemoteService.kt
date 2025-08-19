package org.staacks.alpharemote.service

import android.Manifest
import android.annotation.SuppressLint
import android.companion.AssociationInfo

import android.companion.CompanionDeviceService
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.os.SystemClock
import android.util.Log
import androidx.core.app.ActivityCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import org.staacks.alpharemote.MainActivity
import org.staacks.alpharemote.R
import org.staacks.alpharemote.SettingsStore
import org.staacks.alpharemote.camera.ButtonCode
import org.staacks.alpharemote.camera.CAButton
import org.staacks.alpharemote.camera.CACountdown
import org.staacks.alpharemote.camera.CAJog
import org.staacks.alpharemote.camera.CAWaitFor
import org.staacks.alpharemote.camera.CameraAction
import org.staacks.alpharemote.camera.CameraActionStep
import org.staacks.alpharemote.camera.CameraBLE
import org.staacks.alpharemote.camera.CameraStateReady
import org.staacks.alpharemote.camera.WaitTarget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.LinkedList
import java.util.Timer
import java.util.TimerTask
import kotlin.concurrent.schedule
import kotlin.math.roundToLong
import kotlin.time.Duration.Companion.seconds


class AlphaRemoteService : CompanionDeviceService() {
    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.IO + job)

    private var timer: TimerTask? = null
    private var notificationUI: NotificationUI? = null

    private val  fusedLocationClient: FusedLocationProviderClient by lazy {
        LocationServices.getFusedLocationProviderClient(this)
    }

    private val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY,
        20.seconds.inWholeMilliseconds) // 20 seconds
        .setMinUpdateIntervalMillis(10.seconds.inWholeMilliseconds) // 10 seconds (minimum interval)
        .build()

    private val locationCallback: LocationCallback = object : LocationCallback() {
        override fun onLocationResult(locationResult: LocationResult) {
            val lastLocation = locationResult.lastLocation
            Log.d("LocationUpdates", "Lat: ${lastLocation?.latitude}, Lng: ${lastLocation?.longitude}")
            lastLocation?.let { cameraBLE?.setCameraLocation(it)}

        }
    }

    private fun checkPermissions(): Boolean {
        val fineLocationGranted = ActivityCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarseLocationGranted = ActivityCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        return fineLocationGranted || coarseLocationGranted
    }

    @SuppressLint("MissingPermission")
    private fun startLocationUpdates() {
        if (checkPermissions()) { // Your permission checking function
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                null
            )
            Log.d("LocationUpdates", "Location updates started.")
        }else{
            Log.d("LocationUpdates", "Location updates missing permission.")
        }

    }

    private fun stopLocationUpdates() {
        fusedLocationClient.removeLocationUpdates(locationCallback)
        Log.d("LocationUpdates", "Location updates stopped.")
    }
    companion object {

        private var cameraBLE: CameraBLE? = null
        private var deviceAppearedCount = 0

        private val _serviceState = MutableStateFlow<ServiceState>(ServiceStateGone())
        val serviceState: StateFlow<ServiceState> = _serviceState.asStateFlow()

        fun disconnect() {
            cameraBLE?.disconnectFromDevice()
        }

        const val BUTTON_INTENT_ACTION = "NOTIFICATION_BUTTON"
        const val BUTTON_INTENT_CAMERA_ACTION_EXTRA = "camera_action"
        const val BUTTON_INTENT_CAMERA_ACTION_DOWN_EXTRA = "down"
        const val BUTTON_INTENT_CAMERA_ACTION_UP_EXTRA = "up"

        const val ADVANCED_SEQUENCE_INTENT_ACTION = "ADVANCED_SEQUENCE"
        const val ADVANCED_SEQUENCE_INTENT_BULB_DURATION_EXTRA = "duration"
        const val ADVANCED_SEQUENCE_INTENT_INTERVAL_DURATION_EXTRA = "interval"
        const val ADVANCED_SEQUENCE_INTENT_INTERVAL_COUNT_EXTRA = "count"

        private var pendingActionSteps = LinkedList<CameraActionStep>()
        var broadcastControl = false
    }

    override fun onDeviceAppeared(address: String) {
        Log.d(MainActivity.TAG, "Device appeared: $address")
        try {
            super.onDeviceAppeared(address) //This is abstract on Android 12
        } catch (_: AbstractMethodError) {}

        if (ActivityCompat.checkSelfPermission(applicationContext, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            Log.w(MainActivity.TAG, "Missing Bluetooth permission. Launching activity instead.")
            val intent = Intent(applicationContext, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                putExtra(MainActivity.NAVIGATE_TO_INTENT_EXTRA, R.id.navigation_settings)
            }
            startActivity(intent)
            stopSelf()
            return
        }

        val settingsStore = SettingsStore(application)
        notificationUI = notificationUI ?: (NotificationUI(applicationContext).also { notificationUI ->
            scope.launch {
                settingsStore.customButtonSettings.stateIn(
                    scope = this,
                    started = SharingStarted.WhileSubscribed(5000),
                    initialValue = SettingsStore.CustomButtonSettings(null, 1.0f)
                ).collectLatest {
                    notificationUI.updateCustomButtons(it.customButtonList, it.scale)
                }
            }
            scope.launch {
                settingsStore.permissions.collectLatest {
                    if (it.notification) //Refresh notification if notification permission has been granted after it was not granted previously
                        notificationUI.updateNotification()
                    broadcastControl = it.broadcastControl
                }
            }
        })

        if (cameraBLE == null) {
            cancelPendingActionSteps()
            deviceAppearedCount = 1
            cameraBLE = CameraBLE(scope, application, address, ::onConnect, ::onDisconnect).apply {
                scope.launch {
                    cameraState.collect {
                        when (it) {
                            is CameraStateReady -> {
                                settingsStore.setCameraId(it.name, it.address)
                                checkWaitAction(it)
                            }
                            else -> cancelPendingActionSteps()
                        }
                    }
                }
                scope.launch {
                    cameraState.collectLatest { cameraState ->
                        _serviceState.update {
                            (it as? ServiceRunning)?.copy(cameraState = cameraState) ?: ServiceRunning(cameraState, null, null)
                        }
                        notificationUI?.onCameraStateUpdate(cameraState)
                    }
                }
            }
        } else {
            Log.w(MainActivity.TAG, "onDeviceAppeared ignored as cameraBLE has already been instantiated.")
            deviceAppearedCount++
            return
        }
        startLocationUpdates()
    }

    override fun onDeviceAppeared(associationInfo: AssociationInfo) {
        super.onDeviceAppeared(associationInfo)
        Log.d(MainActivity.TAG, "API33 onDeviceAppeared: $associationInfo")
    }

    override fun onDeviceDisappeared(address: String) {
        Log.d(MainActivity.TAG, "Device disappeared: $address")
        try {
            super.onDeviceDisappeared(address) //This is abstract on Android 12
        } catch (_: AbstractMethodError) {}
        deviceAppearedCount--

        stopLocationUpdates()
        if (deviceAppearedCount == 0) {
            cameraBLE?.disconnectFromDevice()
            cameraBLE = null
            job.cancelChildren()
            stopSelf()
        }
    }

    override fun onDeviceDisappeared(associationInfo: AssociationInfo) {
        super.onDeviceDisappeared(associationInfo)
        Log.d(MainActivity.TAG, "API33 onDeviceDisappeared: $associationInfo")
    }

    private fun onConnect() {
        Log.d(MainActivity.TAG, "onConnect")
        notificationUI?.let {
            startForeground(
                it.notificationId,
                it.start(),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MANIFEST
            )
        }
    }

    private fun onDisconnect() {
        Log.d(MainActivity.TAG, "onDisconnect")
        _serviceState.value = ServiceStateGone()
        cancelPendingActionSteps()
        stopForeground(STOP_FOREGROUND_REMOVE)
        notificationUI?.stop()
        cameraBLE = null
    }

    private fun executeCameraAction(cameraAction: CameraAction, down: Boolean, up: Boolean) {
        var translatedUp = up
        var translatedDown = down

        // Translate toggle release to down or up depending on button state
        if (cameraAction.toggle) {
            translatedDown = false // Toggle only acts on button release. Do not pass through down events
            if (translatedUp) {
                ((serviceState.value as? ServiceRunning)?.cameraState as? CameraStateReady)?.let { cameraState ->
                    translatedUp = cameraAction.preset.template.referenceButton in cameraState.pressedButtons
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

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(MainActivity.TAG, "onStartCommand: $intent")
        when (intent?.action) {
            BUTTON_INTENT_ACTION -> {
                val cameraAction = intent.getSerializableExtra(BUTTON_INTENT_CAMERA_ACTION_EXTRA) as CameraAction
                val down = intent.getBooleanExtra(BUTTON_INTENT_CAMERA_ACTION_DOWN_EXTRA, true)
                val up = intent.getBooleanExtra(BUTTON_INTENT_CAMERA_ACTION_UP_EXTRA, true)

                executeCameraAction(cameraAction, down, up)
            }
            ADVANCED_SEQUENCE_INTENT_ACTION -> {
                val bulbDuration = intent.getSerializableExtra(ADVANCED_SEQUENCE_INTENT_BULB_DURATION_EXTRA) as Float
                val intervalDuration = intent.getSerializableExtra(ADVANCED_SEQUENCE_INTENT_INTERVAL_DURATION_EXTRA) as Float
                val intervalCount = intent.getSerializableExtra(ADVANCED_SEQUENCE_INTENT_INTERVAL_COUNT_EXTRA) as Int

                val stepSequence: MutableList<CameraActionStep> = mutableListOf()
                stepSequence += CAButton(pressed = true, ButtonCode.SHUTTER_HALF)
                if (intervalDuration > 0) {
                    stepSequence += CACountdown(getString(R.string.camera_advanced_interval_timer_label), intervalDuration)
                }
                stepSequence += CAButton(pressed = true, ButtonCode.SHUTTER_FULL, isSequenceTrigger = true)
                if (bulbDuration > 0) {
                    stepSequence += CAWaitFor(WaitTarget.SHUTTER)
                }
                stepSequence += CAButton(pressed = false, ButtonCode.SHUTTER_FULL)
                stepSequence += CAButton(pressed = false, ButtonCode.SHUTTER_HALF)
                if (bulbDuration > 0) {
                    stepSequence += listOf(
                        CACountdown(getString(R.string.camera_advanced_bulb_timer_label), bulbDuration),
                        CAButton(pressed = true, ButtonCode.SHUTTER_HALF),
                        CAButton(pressed = true, ButtonCode.SHUTTER_FULL),
                        CAButton(pressed = false, ButtonCode.SHUTTER_FULL),
                        CAButton(pressed = false, ButtonCode.SHUTTER_HALF),
                    )
                }

                startCameraAction(
                    List(intervalCount) {stepSequence}.flatten()
                )
            }
        }

        return START_NOT_STICKY
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        scope.launch {
            SettingsStore(application).getCustomButtonList().let { customButtonList ->
                SettingsStore(application).getNotificationButtonSize()?.let { notificationButtonSize ->
                    notificationUI?.updateCustomButtons(customButtonList, notificationButtonSize)
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
                cameraBLE?.executeCameraActionStep(CAButton(false, button))
            }
            pendingStepsCancelled = true
            updatePendingActionStatistics()
        }
        _serviceState.update {
            (it as? ServiceRunning)?.copy(countdown = null, countdownLabel = null) ?: it
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
        _serviceState.update {
            (it as? ServiceRunning)?.copy(pendingTriggerCount = pendingTriggerCount) ?: it
        }
    }

    @Synchronized
    fun executeNextCameraActionStep() {
        updatePendingActionStatistics()
        while ((pendingActionSteps.peek() is CAButton || pendingActionSteps.peek() is CAJog)) {
            pendingActionSteps.poll()?.let {
                updatePendingActionStatistics()
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
            _serviceState.update {
                (it as? ServiceRunning)?.copy(countdown = targetTime, countdownLabel = step.label) ?: it
            }
            notificationUI?.showCountdown(targetTime, step.label)
            return
        }
        (serviceState.value as? ServiceRunning)?.let {
            (it.cameraState as? CameraStateReady)?.let { cameraState ->
                checkWaitAction(cameraState)
            }
        }
    }

    @Synchronized
    fun countdownActionComplete() {
        _serviceState.update {
            (it as? ServiceRunning)?.copy(countdown = null, countdownLabel = null) ?: it
        }
        notificationUI?.hideCountdown()
        val nextAction = pendingActionSteps.peek()
        if (nextAction is CACountdown) {
            pendingActionSteps.removeFirst()
            executeNextCameraActionStep()
        }
    }

    @Synchronized
    fun checkWaitAction(state: CameraStateReady) {
        val nextAction = pendingActionSteps.peek()
        if (nextAction is CAWaitFor) {
            when (nextAction.target) {
                WaitTarget.FOCUS -> if (state.focus) {
                    pendingActionSteps.removeFirst()
                    executeNextCameraActionStep()
                }
                WaitTarget.SHUTTER -> if (state.shutter) {
                    pendingActionSteps.removeFirst()
                    executeNextCameraActionStep()
                }
                WaitTarget.RECORDING -> if (state.shutter) {
                    pendingActionSteps.removeFirst()
                    executeNextCameraActionStep()
                }
            }
        }
    }

}