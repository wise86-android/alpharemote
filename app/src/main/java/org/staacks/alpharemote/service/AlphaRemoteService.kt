package org.staacks.alpharemote.service

import android.Manifest
import android.companion.CompanionDeviceService
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.os.SystemClock
import android.util.Log
import androidx.core.app.ActivityCompat
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
import org.staacks.alpharemote.camera.CameraStateIdentified
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
import kotlinx.coroutines.launch
import java.util.LinkedList
import java.util.Timer
import java.util.TimerTask
import kotlin.concurrent.schedule
import kotlin.math.roundToLong


class AlphaRemoteService : CompanionDeviceService() {
    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.IO + job)

    private var timer: TimerTask? = null
    private var notificationUI: NotificationUI? = null

    companion object {

        private var cameraBLE: CameraBLE? = null

        private val _serviceState = MutableStateFlow<ServiceState>(ServiceStateGone())
        val serviceState: StateFlow<ServiceState> = _serviceState.asStateFlow()

        fun disconnect() {
            cameraBLE?.disconnectFromDevice()
        }

        const val BUTTON_INTENT_ACTION = "NOTIFICATION_BUTTON"
        const val BUTTON_INTENT_CAMERA_ACTION_EXTRA = "camera_action"
        const val BUTTON_INTENT_CAMERA_ACTION_DOWN_EXTRA = "down"
        const val BUTTON_INTENT_CAMERA_ACTION_UP_EXTRA = "up"

        private var pendingActionSteps = LinkedList<CameraActionStep>()
        var broadcastControl = false
    }

    override fun onDeviceAppeared(address: String) {
        Log.d("AlphaRemoteService", "Device appeared: $address")
        try {
            super.onDeviceAppeared(address) //This is abstract on Android 12
        } catch (_: AbstractMethodError) {}

        if (ActivityCompat.checkSelfPermission(applicationContext, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            Log.w("Permissions", "Missing Bluetooth permission. Launching activity instead.")
            val intent = Intent(applicationContext, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                putExtra(MainActivity.NAVIGATE_TO_INTENT_EXTRA, R.id.navigation_settings)
            }
            startActivity(intent)
            stopSelf()
            return
        }

        val settingsStore = SettingsStore(application)
        notificationUI = notificationUI ?: NotificationUI(applicationContext)

        if (cameraBLE == null) {
            cancelPendingActionSteps()
            cameraBLE = CameraBLE(scope, application, address, ::onDisconnect).apply {
                scope.launch {
                    cameraState.collect {
                        when (it) {
                            is CameraStateReady -> checkWaitAction(it)
                            is CameraStateIdentified -> settingsStore.setCameraId(it.name, it.address)
                            else -> cancelPendingActionSteps()
                        }
                    }
                }
                scope.launch {
                    cameraState.collectLatest {
                        (serviceState.value as? ServiceRunning)?.let { oldState ->
                            _serviceState.emit(oldState.copy(cameraState = it))
                        } ?: run {
                            _serviceState.emit(ServiceRunning(it, null, null))
                        }
                        notificationUI?.onCameraStateUpdate(it)
                    }
                }
            }
        } else {
            Log.w("BLE", "onDeviceAppeared ignored as cameraBLE has already been instantiated.")
            return
        }

        notificationUI?.let { notificationUI ->
            startForeground(
                notificationUI.notificationId,
                notificationUI.start(),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MANIFEST
            )

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
        }
    }

    override fun onDeviceDisappeared(address: String) {
        Log.d("AlphaRemoteService", "Device disappeared: $address")
        try {
            super.onDeviceDisappeared(address) //This is abstract on Android 12
        } catch (_: AbstractMethodError) {}
        cameraBLE?.disconnectFromDevice()
    }

    private fun onDisconnect() {
        Log.d("AlphaRemoteService", "onDisconnect")
        cameraBLE = null
        cancelPendingActionSteps()
        stopForeground(STOP_FOREGROUND_REMOVE)
        notificationUI?.stop()
        scope.launch {
            _serviceState.emit(ServiceStateGone())
            job.cancelChildren()
        }
        stopSelf()
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
        Log.d("AlphaRemoteService", "onStartCommand: $intent")
        when (intent?.action) {
            BUTTON_INTENT_ACTION -> {
                val cameraAction = intent.getSerializableExtra(BUTTON_INTENT_CAMERA_ACTION_EXTRA) as CameraAction
                val down = intent.getBooleanExtra(BUTTON_INTENT_CAMERA_ACTION_DOWN_EXTRA, true)
                val up = intent.getBooleanExtra(BUTTON_INTENT_CAMERA_ACTION_UP_EXTRA, true)

                executeCameraAction(cameraAction, down, up)
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
    fun cancelPendingActionSteps() {
        timer?.cancel()
        if (pendingActionSteps.isNotEmpty()) {
            pendingActionSteps.clear()
            for (button in ButtonCode.entries) {
                cameraBLE?.executeCameraActionStep(CAButton(false, button))
            }
        }
        (serviceState.value as? ServiceRunning)?.let { oldState ->
            scope.launch { _serviceState.emit(oldState.copy(countdown = null, countdownLabel = null)) }
        }
        notificationUI?.hideCountdown()
    }

    @Synchronized
    fun startCameraAction(steps: List<CameraActionStep>) {
        cancelPendingActionSteps()
        pendingActionSteps.addAll(steps)
        executeNextCameraActionStep()
    }

    @Synchronized
    fun executeNextCameraActionStep() {
        while ((pendingActionSteps.peek() is CAButton || pendingActionSteps.peek() is CAJog)) {
            pendingActionSteps.poll()?.let {
                cameraBLE?.executeCameraActionStep(it)
            }
        }
        (pendingActionSteps.peek() as? CACountdown)?.let {
            val time = (it.duration * 1000).roundToLong()
            timer?.cancel()
            timer = Timer().schedule(time) {
                countdownActionComplete()
            }
            val targetTime = SystemClock.elapsedRealtime() + time
            (serviceState.value as? ServiceRunning)?.let { oldState ->
                scope.launch { _serviceState.emit(oldState.copy(countdown = targetTime, countdownLabel = it.label)) }
            }
            notificationUI?.showCountdown(targetTime, it.label)
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
        (serviceState.value as? ServiceRunning)?.let { oldState ->
            scope.launch { _serviceState.emit(oldState.copy(countdown = null, countdownLabel = null)) }
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