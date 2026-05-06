package org.staacks.alpharemote.ui.camera

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.Surface
import android.view.View
import android.view.ViewGroup
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.staacks.alpharemote.MainActivity
import org.staacks.alpharemote.R
import org.staacks.alpharemote.data.SettingsStore
import org.staacks.alpharemote.camera.CameraAction
import org.staacks.alpharemote.camera.CameraActionPreset
import org.staacks.alpharemote.service.AlphaRemoteService
import org.staacks.alpharemote.ui.help.HelpDialogFragment
import org.staacks.alpharemote.ui.theme.BluetoothRemoteForSonyCamerasTheme
import java.io.Serializable


class CameraFragment : Fragment() {

    private lateinit var cameraViewModel: CameraViewModel

    private var customButtons: List<CameraAction> by mutableStateOf(emptyList())
    private var mService: AlphaRemoteService? = null

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            val binder = service as AlphaRemoteService.LocalBinder
            mService = binder.getService()
        }

        override fun onServiceDisconnected(arg0: ComponentName) {
            mService = null
        }
    }

    override fun onStart() {
        super.onStart()
        requireContext().let { context ->
            context.bindService(Intent(context, AlphaRemoteService::class.java), connection, Context.BIND_AUTO_CREATE)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        cameraViewModel = ViewModelProvider(this)[CameraViewModel::class.java]

        observeViewModelActions()
        observeSettings()

        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                val uiState by cameraViewModel.uiState.collectAsStateWithLifecycle()

                BluetoothRemoteForSonyCamerasTheme {
                    Surface {
                        CameraScreen(
                            uiState = uiState,
                            customButtons = customButtons,
                            onGotoSettings = { cameraViewModel.gotoDeviceSettings() },
                            onHelp = { cameraViewModel.helpRemote() },
                            onDefaultRemoteTouch = { button, action ->
                                cameraViewModel.onDefaultRemoteButtonTouch(
                                    button,
                                    action
                                )
                            },
                            onBulbToggleChanged = { cameraViewModel.uiState.value.bulbToggle = it },
                            onBulbDurationChanged = {
                                cameraViewModel.uiState.value.bulbDuration = it.toDoubleOrNull()
                            },
                            onIntervalToggleChanged = {
                                cameraViewModel.uiState.value.intervalToggle = it
                            },
                            onIntervalCountChanged = {
                                cameraViewModel.uiState.value.intervalCount = it.toIntOrNull()
                            },
                            onIntervalDurationChanged = {
                                cameraViewModel.uiState.value.intervalDuration = it.toDoubleOrNull()
                            },
                            onStartSequence = { cameraViewModel.startAdvancedSequence() },
                            onCustomButtonClick = { sendCameraActionToService(it, null) },
                        )
                    }

                }
            }
        }
    }

    private fun observeViewModelActions() {
        viewLifecycleOwner.lifecycleScope.launch {
            cameraViewModel.uiAction.collect { action ->
                when (action) {
                    is CameraViewModel.GenericCameraUIAction -> handleGenericUIAction(action)
                    is CameraViewModel.DefaultRemoteButtonCameraUIAction -> handleDefaultRemoteButtonAction(action)
                }
            }
        }
    }

    private fun observeSettings() {
        val settingsStore = SettingsStore(requireContext())
        viewLifecycleOwner.lifecycleScope.launch {
            settingsStore.customButtonSettings.stateIn(
                scope = this,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = SettingsStore.CustomButtonSettings(null, 1.0f)
            ).collectLatest {
                customButtons = it.customButtonList ?: emptyList()
            }
        }
    }

    // --- Action Handlers ---

    private fun handleGenericUIAction(action: CameraViewModel.GenericCameraUIAction) {
        when (action.action) {
            CameraViewModel.GenericCameraUIActionType.GOTO_DEVICE_SETTINGS -> gotoDeviceSettings()
            CameraViewModel.GenericCameraUIActionType.HELP_REMOTE ->
                HelpDialogFragment.newInstance(
                    R.string.help_camera_remote_title,
                    R.string.help_camera_remote_text
                ).show(childFragmentManager, null)
            CameraViewModel.GenericCameraUIActionType.START_ADVANCED_SEQUENCE -> startAdvancedSequence()
        }
    }

    private fun handleDefaultRemoteButtonAction(action: CameraViewModel.DefaultRemoteButtonCameraUIAction) {
        when (action.button) {
            DefaultRemoteButton.Button.SHUTTER ->
                sendCameraActionToService(CameraAction(false, null, null, null, CameraActionPreset.SHUTTER), action.event)
            DefaultRemoteButton.Button.SHUTTER_HALF ->
                sendCameraActionToService(CameraAction(true, null, null, null, CameraActionPreset.SHUTTER_HALF), action.event)
            DefaultRemoteButton.Button.SELFTIMER_3S -> {
                if (action.event == MotionEvent.ACTION_UP) {
                    sendCameraActionToService(CameraAction(false, 3.0f, null, null, CameraActionPreset.TRIGGER_ONCE), null)
                }
            }
            DefaultRemoteButton.Button.RECORD ->
                sendCameraActionToService(CameraAction(false, null, null, null, CameraActionPreset.RECORD), action.event)
            DefaultRemoteButton.Button.C1 ->
                sendCameraActionToService(CameraAction(false, null, null, null, CameraActionPreset.C1), action.event)
            DefaultRemoteButton.Button.AF_ON ->
                sendCameraActionToService(CameraAction(true, null, null, null, CameraActionPreset.AF_ON), action.event)
            DefaultRemoteButton.Button.ZOOM_IN ->
                sendCameraActionToService(CameraAction(false, null, null, null, CameraActionPreset.ZOOM_IN), action.event)
            DefaultRemoteButton.Button.ZOOM_OUT ->
                sendCameraActionToService(CameraAction(false, null, null, null, CameraActionPreset.ZOOM_OUT), action.event)
            DefaultRemoteButton.Button.FOCUS_FAR ->
                sendCameraActionToService(CameraAction(false, null, null, null, CameraActionPreset.FOCUS_FAR), action.event)
            DefaultRemoteButton.Button.FOCUS_NEAR ->
                sendCameraActionToService(CameraAction(false, null, null, null, CameraActionPreset.FOCUS_NEAR), action.event)
        }
    }

    private fun sendCameraActionToService(cameraAction: CameraAction, event: Int?) {
        val intent = Intent(context, AlphaRemoteService::class.java).apply {
            action = AlphaRemoteService.BUTTON_INTENT_ACTION
            putExtra(AlphaRemoteService.BUTTON_INTENT_CAMERA_ACTION_EXTRA, cameraAction as Serializable)
            event?.let {
                if (event == MotionEvent.ACTION_DOWN)
                    putExtra(AlphaRemoteService.BUTTON_INTENT_CAMERA_ACTION_UP_EXTRA, false)
                else
                    putExtra(AlphaRemoteService.BUTTON_INTENT_CAMERA_ACTION_DOWN_EXTRA, false)
            }
        }
        requireContext().startService(intent)
    }

    private fun gotoDeviceSettings() {
        (activity as MainActivity).navigateTo(R.id.navigation_settings)
    }

    private fun startAdvancedSequence() {
        cameraViewModel.uiState.value.let { uiState ->
            val bulbDuration = if (uiState.bulbToggle) { uiState.bulbDuration ?: 0.0 } else { 0.0 }
            val intervalCount = if (uiState.intervalToggle) { uiState.intervalCount ?: 1 } else { 1 }
            val intervalDuration = if (uiState.intervalToggle) { uiState.intervalDuration ?: 0.0 } else { 0.0 }

            val intent = Intent(context, AlphaRemoteService::class.java).apply {
                action = AlphaRemoteService.ADVANCED_SEQUENCE_INTENT_ACTION
                putExtra(AlphaRemoteService.ADVANCED_SEQUENCE_INTENT_BULB_DURATION_EXTRA, bulbDuration.toFloat())
                putExtra(AlphaRemoteService.ADVANCED_SEQUENCE_INTENT_INTERVAL_COUNT_EXTRA, intervalCount)
                putExtra(AlphaRemoteService.ADVANCED_SEQUENCE_INTENT_INTERVAL_DURATION_EXTRA, intervalDuration.toFloat())
            }
            requireContext().startService(intent)
        }
    }


    override fun onStop() {
        super.onStop()
        requireContext().unbindService(connection)
    }
}