package org.staacks.alpharemote.ui.camera

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.res.ColorStateList
import android.os.Bundle
import android.os.IBinder
import android.text.Editable
import android.text.TextWatcher
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
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
import org.staacks.alpharemote.camera.CameraState
import org.staacks.alpharemote.databinding.FragmentCameraBinding
import org.staacks.alpharemote.service.AlphaRemoteService
import org.staacks.alpharemote.ui.help.HelpDialogFragment
import java.io.Serializable


class CameraFragment : Fragment() {

    private var _binding: FragmentCameraBinding? = null
    private val binding get() = _binding!!
    private var cameraViewModel: CameraViewModel? = null

    private var customButtons: List<CameraAction>? = null
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
        _binding = FragmentCameraBinding.inflate(inflater, container, false)

        setupWindowInsets()
        setupListeners()
        observeViewModel()
        observeSettings()

        return binding.root
    }

    private fun setupWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.constraintLayoutInset) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            v.updatePadding(left = bars.left, top = bars.top, right = bars.right, bottom = 0)
            WindowInsetsCompat.CONSUMED
        }
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            cameraViewModel?.uiState?.collect { state ->
                updateMainUIState(state)
                updateCameraStatusUI(state)
                updateServiceStatusUI(state)
                updateAdvancedControlsUI(state)
                updateRemoteButtonsUI(state)
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            cameraViewModel?.uiAction?.collect { action ->
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
                customButtons = it.customButtonList
                updateCustomButtonsList(it.customButtonList)
            }
        }
    }

    // --- UI Update Functions (State) ---

    private fun updateMainUIState(state: CameraViewModel.CameraUIState) {
        val isConnected = state.connected
        binding.disconnectMsg.visibility = if (isConnected) View.GONE else View.VISIBLE
        binding.buttonGotoSettings.visibility = if (isConnected) View.GONE else View.VISIBLE
        binding.constraintLayoutInset.visibility = if (isConnected) View.VISIBLE else View.GONE
        binding.advancedControls.root.visibility = if (isConnected) View.VISIBLE else View.GONE
    }

    private fun updateCameraStatusUI(state: CameraViewModel.CameraUIState) {
        val cameraState = state.cameraState
        binding.statusName.text = cameraState?.name ?: getString(R.string.settings_camera_unknown_name)
        binding.statusFocus.alpha = if (cameraState?.focus == true) 1.0f else 0.5f
        binding.statusShutter.alpha = if (cameraState?.shutter == true) 1.0f else 0.5f
        binding.statusRecording.alpha = if (cameraState?.recording == true) 1.0f else 0.5f
    }

    private fun updateServiceStatusUI(state: CameraViewModel.CameraUIState) {
        val serviceState = state.serviceState
        binding.buttonHelp.visibility = if (serviceState?.countdownLabel == null) View.VISIBLE else View.INVISIBLE
        binding.statusAction.visibility = if (serviceState?.countdownLabel == null) View.INVISIBLE else View.VISIBLE
        binding.statusAction.text = serviceState?.countdownLabel

        binding.statusCountdown.visibility = if (serviceState?.countdown == null) View.INVISIBLE else View.VISIBLE
        serviceState?.countdown?.let {
            binding.statusCountdown.base = it
            binding.statusCountdown.start()
        }
    }

    private fun updateAdvancedControlsUI(state: CameraViewModel.CameraUIState) {
        binding.advancedControls.apply {
            val bulbEnabled = state.bulbToggle
            bulbToggle.isChecked = bulbEnabled
            bulbDuration.isEnabled = bulbEnabled
            bulbDurationLabel.alpha = if (bulbEnabled) 1.0f else 0.5f
            bulbDurationUnit.alpha = if (bulbEnabled) 1.0f else 0.5f
            syncEditText(bulbDuration, state.bulbDuration?.toString())

            val intervalEnabled = state.intervalToggle
            intervalToggle.isChecked = intervalEnabled
            intervalCount.isEnabled = intervalEnabled
            intervalCountLabel.alpha = if (intervalEnabled) 1.0f else 0.5f
            intervalDuration.isEnabled = intervalEnabled
            intervalDurationLabel.alpha = if (intervalEnabled) 1.0f else 0.5f
            intervalDurationUnit.alpha = if (intervalEnabled) 1.0f else 0.5f
            syncEditText(intervalCount, state.intervalCount?.toString())
            syncEditText(intervalDuration, state.intervalDuration?.toString())

            val pendingCount = state.serviceState?.pendingTriggerCount ?: 0
            pendingTriggers.visibility = if (pendingCount > 0) View.VISIBLE else View.GONE
            if (pendingCount > 0) {
                pendingTriggers.text = getString(R.string.camera_advanced_pending_triggers, pendingCount)
            }

            startSequenceAction.text = if (state.serviceState?.countdown == null)
                getString(R.string.camera_advanced_start)
            else
                getString(R.string.camera_advanced_abort)
            startSequenceAction.isEnabled = (bulbEnabled || intervalEnabled)
        }
    }

    private fun updateRemoteButtonsUI(state: CameraViewModel.CameraUIState) {
        state.cameraState?.let { readyState ->
            binding.defaultRemote.apply {
                buttonAfOn.updateCameraState(readyState)
                buttonC1.updateCameraState(readyState)
                buttonFocusFar.updateCameraState(readyState)
                buttonFocusNear.updateCameraState(readyState)
                buttonRecord.updateCameraState(readyState)
                buttonSelftimer3s.updateCameraState(readyState)
                buttonShutter.updateCameraState(readyState)
                buttonShutterHalf.updateCameraState(readyState)
                buttonZoomIn.updateCameraState(readyState)
                buttonZoomOut.updateCameraState(readyState)
            }
            updateCustomButtonsVisualState(readyState)
        }
    }

    private fun syncEditText(editText: android.widget.EditText, newValue: String?) {
        val text = newValue ?: ""
        if (editText.text.toString() != text) {
            editText.setText(text)
        }
    }

    // --- Action Handlers ---

    private fun handleGenericUIAction(action: CameraViewModel.GenericCameraUIAction) {
        when (action.action) {
            CameraViewModel.GenericCameraUIActionType.GOTO_DEVICE_SETTINGS -> gotoDeviceSettings()
            CameraViewModel.GenericCameraUIActionType.HELP_REMOTE ->
                HelpDialogFragment().setContent(
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

    // --- Listener Setup ---

    private fun setupListeners() {
        binding.buttonGotoSettings.setOnClickListener { cameraViewModel?.gotoDeviceSettings() }
        binding.buttonHelp.setOnClickListener { cameraViewModel?.helpRemote() }

        setupDefaultRemoteListeners()
        setupAdvancedControlsListeners()
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupDefaultRemoteListeners() {
        val onTouchListener = View.OnTouchListener { v, event ->
            cameraViewModel?.defaultRemoteButtonOnTouchListener(v, event) ?: false
        }

        binding.defaultRemote.apply {
            buttonShutter.setOnTouchListener(onTouchListener)
            buttonShutterHalf.setOnTouchListener(onTouchListener)
            buttonSelftimer3s.setOnTouchListener(onTouchListener)
            buttonRecord.setOnTouchListener(onTouchListener)
            buttonAfOn.setOnTouchListener(onTouchListener)
            buttonC1.setOnTouchListener(onTouchListener)
            buttonFocusFar.setOnTouchListener(onTouchListener)
            buttonFocusNear.setOnTouchListener(onTouchListener)
            buttonZoomIn.setOnTouchListener(onTouchListener)
            buttonZoomOut.setOnTouchListener(onTouchListener)
        }
    }

    private fun setupAdvancedControlsListeners() {
        binding.advancedControls.apply {
            bulbToggle.setOnCheckedChangeListener { _, isChecked ->
                cameraViewModel?.uiState?.value?.bulbToggle = isChecked
            }
            bulbDuration.addTextChangedListener(createSimpleTextWatcher { s ->
                cameraViewModel?.uiState?.value?.bulbDuration = s.toDoubleOrNull()
            })

            intervalToggle.setOnCheckedChangeListener { _, isChecked ->
                cameraViewModel?.uiState?.value?.intervalToggle= isChecked
            }
            intervalCount.addTextChangedListener(createSimpleTextWatcher { s ->
                cameraViewModel?.uiState?.value?.intervalCount = s.toIntOrNull()
            })
            intervalDuration.addTextChangedListener(createSimpleTextWatcher { s ->
                cameraViewModel?.uiState?.value?.intervalDuration = s.toDoubleOrNull()
            })

            startSequenceAction.setOnClickListener {
                cameraViewModel?.startAdvancedSequence()
            }
        }
    }

    private fun createSimpleTextWatcher(afterChanged: (String) -> Unit) = object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        override fun afterTextChanged(s: Editable?) { afterChanged(s?.toString() ?: "") }
    }

    // --- Helper Functions ---

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

    private fun updateCustomButtonsList(buttons: List<CameraAction>?) {
        binding.advancedControls.customButtons.removeAllViews()
        val context = binding.advancedControls.customButtons.context
        val parentID = binding.advancedControls.customButtons.id
        val unset = ConstraintLayout.LayoutParams.UNSET
        var previousID = -1
        var nextID = -1

        val ripple = TypedValue()
        context.theme.resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, ripple, true)

        val colorAttr = TypedValue()
        context.theme.resolveAttribute(R.attr.colorCustomButton, colorAttr, true)
        val color = context.getColor(colorAttr.resourceId)

        buttons?.let { list ->
            val n = list.size
            list.forEachIndexed { i, cameraAction ->
                val imageView = ImageView(context)
                imageView.setImageDrawable(cameraAction.getIcon(context))
                imageView.setBackgroundResource(ripple.resourceId)
                imageView.imageTintList = ColorStateList.valueOf(color)
                imageView.isClickable = true
                imageView.setOnClickListener { sendCameraActionToService(cameraAction, null) }

                imageView.id = if (i == 0) View.generateViewId() else nextID
                if (i != n - 1) nextID = View.generateViewId()
                
                imageView.layoutParams = ConstraintLayout.LayoutParams(0, 0)
                imageView.updateLayoutParams<ConstraintLayout.LayoutParams> {
                    startToStart = if (i == 0) parentID else unset
                    startToEnd = if (i == 0) unset else previousID
                    endToEnd = if (i == n - 1) parentID else unset
                    endToStart = if (i == n - 1) unset else nextID
                    topToTop = parentID
                    bottomToBottom = parentID
                    dimensionRatio = "1:1"
                }
                previousID = imageView.id
                binding.advancedControls.customButtons.addView(imageView)
            }
        }
    }

    private fun updateCustomButtonsVisualState(cameraState: CameraState) {
        val context = context ?: return
        customButtons?.let { list ->
            val colorAttr = TypedValue()
            context.theme.resolveAttribute(R.attr.colorCustomButton, colorAttr, true)
            val baseColor = context.getColor(colorAttr.resourceId)
            context.theme.resolveAttribute(com.google.android.material.R.attr.colorSecondary, colorAttr, true)
            val pressedColor = context.getColor(colorAttr.resourceId)
            val disabledColor = context.getColor(R.color.gray50)

            list.forEachIndexed { i, cameraAction ->
                if (!cameraAction.preset.template.preserveColor) {
                    val color = if (cameraState is CameraState.Ready) {
                        if (cameraAction.preset.template.referenceButton in cameraState.pressedButtons || 
                            cameraAction.preset.template.referenceJog in cameraState.pressedJogs)
                            pressedColor else baseColor
                    } else {
                        disabledColor
                    }
                    (binding.advancedControls.customButtons.getChildAt(i) as? ImageView)?.imageTintList = ColorStateList.valueOf(color)
                }
            }
        }
    }

    private fun gotoDeviceSettings() {
        (activity as MainActivity).navigateTo(R.id.navigation_settings)
    }

    private fun startAdvancedSequence() {
        cameraViewModel?.uiState?.value?.let { uiState ->
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

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onStop() {
        super.onStop()
        requireContext().unbindService(connection)
    }
}