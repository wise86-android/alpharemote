package org.staacks.alpharemote.ui.settings

import android.Manifest
import android.animation.LayoutTransition
import android.app.Activity
import android.app.AlertDialog
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanResult
import android.companion.CompanionDeviceManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.IntentSender
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CompoundButton
import android.widget.SeekBar
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.Lifecycle
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.ItemTouchHelper.ACTION_STATE_DRAG
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import org.staacks.alpharemote.MainActivity
import org.staacks.alpharemote.R
import org.staacks.alpharemote.camera.CameraAction
import org.staacks.alpharemote.databinding.FragmentSettingsBinding
import org.staacks.alpharemote.service.AlphaRemoteService
import org.staacks.alpharemote.ui.help.HelpDialogFragment
import org.staacks.alpharemote.ui.settings.CompanionDeviceHelper.pairCompanionDevice
import org.staacks.alpharemote.ui.settings.CompanionDeviceHelper.startObservingDevicePresence
import org.staacks.alpharemote.ui.theme.BluetoothRemoteForSonyCamerasTheme
import org.staacks.alpharemote.utils.hasBluetoothPermission

interface CustomButtonListEventReceiver {
    fun startDragging(viewHolder: RecyclerView.ViewHolder)
    fun itemTouched(index: Int, oldCameraAction: CameraAction)
}

class SettingsFragment : Fragment(), CustomButtonListEventReceiver, CameraActionPickerListener {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    lateinit var adapter: CustomButtonRecyclerViewAdapter

    private var itemTouchHelper: ItemTouchHelper? = null

    val onDeviceFoundLauncher = registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { activityResult  ->
        Log.d(MainActivity.TAG, "Activity Result: $activityResult")
        if (activityResult.resultCode == Activity.RESULT_OK) {
            val scanResult: ScanResult? = activityResult.data?.getParcelableExtra(
                CompanionDeviceManager.EXTRA_DEVICE)
            scanResult?.let { result ->
                if (startObservingDevicePresence(requireContext(), result.device)) {
                    if (!checkNotificationPermissionState())
                        requestNotificationPermission(false)
                }
                checkAssociations()
            }
        }
    }

    override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?
    ): View {
        val settingsViewModel = ViewModelProvider(this)[SettingsViewModel::class.java]

        _binding = FragmentSettingsBinding.inflate(inflater, container, false)

        binding.composePermissionRequest.apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                // In Compose world
                BluetoothRemoteForSonyCamerasTheme {
                    LocationSettings(settingsViewModel)
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    settingsViewModel.uiAction.collect { action ->
                        when (action) {
                            SettingsViewModel.SettingsUIAction.PAIR -> pair()
                            SettingsViewModel.SettingsUIAction.UNPAIR -> unpair()
                            SettingsViewModel.SettingsUIAction.REQUEST_BLUETOOTH_PERMISSION -> requestBluetoothPermission(bluetoothRequestPermissionLauncher, true)
                            SettingsViewModel.SettingsUIAction.REQUEST_NOTIFICATION_PERMISSION -> requestNotificationPermission(true)
                            SettingsViewModel.SettingsUIAction.ADD_CUSTOM_BUTTON -> addCustomButton()
                            SettingsViewModel.SettingsUIAction.HELP_CONNECTION ->
                                HelpDialogFragment().setContent(
                                    R.string.help_settings_connection_troubleshooting_title,
                                    R.string.help_settings_connection_troubleshooting_text
                                ).show(childFragmentManager, null)
                            SettingsViewModel.SettingsUIAction.HELP_CUSTOM_BUTTONS ->
                                HelpDialogFragment().setContent(
                                    R.string.help_settings_custom_buttons_title,
                                    R.string.help_settings_custom_buttons_text
                                ).show(childFragmentManager, null)
                        }
                    }
                }

                launch {
                    settingsViewModel.uiState.collect { state ->
                         updateUI(state)
                    }
                }

                launch {
                   settingsViewModel.buttonScaleIndex.collect { index ->
                       if (binding.seekbarButtonScale.progress != index) {
                           binding.seekbarButtonScale.progress = index
                       }
                   }
                }

                launch {
                    settingsViewModel.broadcastControl.collect { enabled ->
                       if (binding.switchBroadcastControl.isChecked != enabled) {
                           binding.switchBroadcastControl.isChecked = enabled
                       }
                    }
                }
            }
        }

        setupCustomButtonList(settingsViewModel.customButtonListFlow)

        binding.linearLayout.layoutTransition.enableTransitionType(LayoutTransition.CHANGING)

        ViewCompat.setOnApplyWindowInsetsListener(binding.linearLayout) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            v.updatePadding(
                left = bars.left,
                top = bars.top,
                right = bars.right,
                bottom = bars.bottom,
            )
            WindowInsetsCompat.CONSUMED
        }

        context?.registerReceiver(bondStateReceiver, IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED))
        context?.registerReceiver(bluetoothStateReceiver, IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED))
        context?.registerReceiver(locationServiceStateReceiver, IntentFilter(LocationManager.MODE_CHANGED_ACTION))

        setupViewListeners(settingsViewModel)

        checkBluetoothState()
        checkLocationServiceState()
        checkBluetoothPermissionState()
        checkNotificationPermissionState()
        checkAssociations()

        return binding.root
    }

    private fun setupViewListeners(settingsViewModel: SettingsViewModel) {
        // Set up click listeners
        binding.btnBluetoothPermission.setOnClickListener { settingsViewModel.requestBluetoothPermission() }
        binding.btnNotificationPermission.setOnClickListener { settingsViewModel.requestNotificationPermission() }
        binding.btnCameraAdd.setOnClickListener { settingsViewModel.pair() }
        binding.btnCameraRemove.setOnClickListener { settingsViewModel.unpair() }
        binding.btnHelpConnection.setOnClickListener { settingsViewModel.helpConnection() }
        binding.addCustomButton.setOnClickListener { settingsViewModel.addCustomButton() }
        binding.btnHelpCustomButtons.setOnClickListener { settingsViewModel.helpCustomButtons() }
        binding.buttonScaleSmaller.setOnClickListener { settingsViewModel.decrementButtonScale() }
        binding.buttonScaleLarger.setOnClickListener { settingsViewModel.incrementButtonScale() }
        binding.btnBroadcastControlMore.setOnClickListener { openURL(getString(R.string.settings_broadcast_control_more_url)) }

        // seekbar setup
        binding.seekbarButtonScale.max = settingsViewModel.buttonScaleSteps.size - 1
        binding.seekbarButtonScale.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (seekBar != null) settingsViewModel.setButtonScale(seekBar, progress, fromUser)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // switch setup
        binding.switchBroadcastControl.setOnCheckedChangeListener { buttonView, isChecked ->
            settingsViewModel.setBroadcastControl(buttonView, isChecked)
        }
    }

    private fun updateUI(state: SettingsViewModel.SettingsUIState) {
        // Missing permissions
        val missingBluetooth = !state.bluetoothPermissionGranted
        val missingNotification = !state.notificationPermissionGranted
        val cameraAssociated = state.cameraState != SettingsViewModel.SettingsUICameraState.NOT_ASSOCIATED

        binding.tvMissingPermissionsTitle.visibility = if (cameraAssociated && (missingBluetooth || missingNotification)) View.VISIBLE else View.GONE
        binding.tvMissingBluetoothPermission.visibility = if (cameraAssociated && missingBluetooth) View.VISIBLE else View.GONE
        binding.btnBluetoothPermission.visibility = if (cameraAssociated && missingBluetooth) View.VISIBLE else View.GONE
        binding.tvMissingNotificationPermission.visibility = if (cameraAssociated && missingNotification) View.VISIBLE else View.GONE
        binding.btnNotificationPermission.visibility = if (cameraAssociated && missingNotification) View.VISIBLE else View.GONE

        // Location service
        binding.tvLocationServiceDisabled.visibility = if (!state.locationServiceEnabled) View.VISIBLE else View.GONE

        // Camera
        binding.tvBluetoothDisabled.visibility = if (!state.bluetoothEnabled) View.VISIBLE else View.GONE
        binding.tvBleScanningDisabled.visibility = if (!state.bleScanningEnabled) View.VISIBLE else View.GONE

        val cameraName = state.cameraName ?: getString(R.string.settings_camera_unknown_name)

        binding.tvCameraOffline.visibility = if (state.cameraState == SettingsViewModel.SettingsUICameraState.OFFLINE) View.VISIBLE else View.GONE
        binding.tvCameraOffline.text = getString(R.string.settings_camera_offline, cameraName)

        binding.tvCameraConnected.visibility = if (state.cameraState == SettingsViewModel.SettingsUICameraState.CONNECTED) View.VISIBLE else View.GONE
        binding.tvCameraConnected.text = getString(R.string.settings_camera_connected, cameraName)

        binding.tvCameraError.visibility = if (state.cameraError != null) View.VISIBLE else View.GONE
        binding.tvCameraError.text = getString(R.string.settings_camera_error, state.cameraError)

        binding.tvCameraNotAssociated.visibility = if (state.cameraState == SettingsViewModel.SettingsUICameraState.NOT_ASSOCIATED) View.VISIBLE else View.GONE

        binding.tvCameraNotBonded.visibility = if (state.cameraState == SettingsViewModel.SettingsUICameraState.NOT_BONDED) View.VISIBLE else View.GONE
        binding.tvCameraNotBonded.text = getString(R.string.settings_camera_not_bonded)

        binding.tvCameraRemoteDisabled.visibility = if (state.cameraState == SettingsViewModel.SettingsUICameraState.REMOTE_DISABLED) View.VISIBLE else View.GONE
        binding.tvCameraRemoteDisabled.text = getString(R.string.settings_camera_remote_disabled)

        binding.btnCameraAdd.visibility = if (state.cameraState == SettingsViewModel.SettingsUICameraState.NOT_ASSOCIATED) View.VISIBLE else View.GONE
        binding.btnCameraAdd.isEnabled = state.bluetoothEnabled

        binding.btnCameraRemove.visibility = if (state.cameraState != SettingsViewModel.SettingsUICameraState.NOT_ASSOCIATED) View.VISIBLE else View.GONE
    }

    override fun onResume() {
        super.onResume()

        // While a state change of the location service is captured by a BroadcastReceiver, we
        // have no other method to detect a change of the "Bluetooth Scanning" setting if the user
        // just switched to its settings to toggle it.
        checkLocationServiceState()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        context?.unregisterReceiver(bondStateReceiver)
        context?.unregisterReceiver(bluetoothStateReceiver)
        context?.unregisterReceiver(locationServiceStateReceiver)
        _binding = null
    }

    private val bondStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            Log.d(MainActivity.TAG, "SettingsFragment received BluetoothDevice.ACTION_BOND_STATE_CHANGED.")
            checkAssociations()
        }
    }

    private val bluetoothStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            Log.d(MainActivity.TAG, "SettingsFragment received BluetoothAdapter.ACTION_STATE_CHANGED.")
            checkBluetoothState()
        }
    }

    private val locationServiceStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            Log.d(MainActivity.TAG, "SettingsFragment received LocationManager.MODE_CHANGED_ACTION.")
            checkLocationServiceState()
        }
    }

    private fun pair() {
        if (checkBluetoothPermissionState())
            executePair()
        else
            requestBluetoothPermission(pairAfterBluetoothRequestPermissionLauncher, false)
    }

    private fun executePair() {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.settings_camera_add)
            .setMessage(R.string.settings_camera_add_info)
            .setCancelable(true)
            .setPositiveButton(R.string.settings_camera_add_confirm) { dialog, which ->
                pairCompanionDevice(requireContext(), object : CompanionDeviceManager.Callback() {
                    override fun onDeviceFound(chooserLauncher: IntentSender) {
                        Log.d(MainActivity.TAG, "onDeviceFound")
                        onDeviceFoundLauncher.launch(IntentSenderRequest.Builder(chooserLauncher).build(), null)
                    }

                    override fun onFailure(error: CharSequence?) {
                        Log.d(MainActivity.TAG, "onFailure")
                        val viewModel = ViewModelProvider(this@SettingsFragment)[SettingsViewModel::class.java]
                        viewModel.reportErrorState(error.toString())
                    }
                })
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun unpair() {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.settings_camera_remove)
            .setMessage(R.string.settings_camera_remove_question)
            .setCancelable(true)
            .setPositiveButton(R.string.settings_camera_remove_confirm) { dialog, which ->
                val context = requireContext()
                CompanionDeviceHelper.unpairCompanionDevice(context)
                AlphaRemoteService.sendDisconnectIntent(requireContext())
                checkAssociations()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun checkAssociations() {

        val address = CompanionDeviceHelper.getAssociation(requireContext()).firstOrNull()
        val isAssociated = address != null
        val isBonded = isAssociated && try {
            val adapter = ContextCompat.getSystemService(requireContext(), BluetoothManager::class.java)?.adapter
            adapter?.getRemoteDevice(address)?.bondState == BluetoothDevice.BOND_BONDED
        } catch (_: SecurityException) {
            false
        }

        val viewModel = ViewModelProvider(this)[SettingsViewModel::class.java]
        viewModel.updateAssociationState(address, isAssociated, isBonded)
    }

    private fun checkBluetoothState() {
        val adapter = ContextCompat.getSystemService(requireContext(), BluetoothManager::class.java)?.adapter
        val enabled = adapter?.state == BluetoothAdapter.STATE_ON
        val viewModel = ViewModelProvider(this)[SettingsViewModel::class.java]
        viewModel.updateBluetoothState(enabled)
    }

    private fun checkLocationServiceState() {
        val locationManager = context?.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val bleScanning = try {
            Settings.Global.getInt(context?.contentResolver, "ble_scan_always_enabled") == 1
        } catch (_: Exception) {
            true // In this case, the setting has probably never been touched, which should be fine.
        }
        val viewModel = ViewModelProvider(this)[SettingsViewModel::class.java]
        viewModel.updateLocationServiceState(locationManager.isLocationEnabled, bleScanning)
    }

    private fun checkBluetoothPermissionState(): Boolean {
        val bluetoothGranted = hasBluetoothPermission(requireContext())
        val viewModel = ViewModelProvider(this)[SettingsViewModel::class.java]
        viewModel.updateBluetoothPermissionState(bluetoothGranted)
        return bluetoothGranted
    }

    private fun checkNotificationPermissionState(): Boolean {
        val notificationsGranted = (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU
                || (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED))
        val viewModel = ViewModelProvider(this)[SettingsViewModel::class.java]
        viewModel.updateNotificationPermissionState(notificationsGranted)
        return notificationsGranted
    }

    private fun requestBluetoothPermission(launcher: ActivityResultLauncher<String>, skipRationale: Boolean) {
        if (!skipRationale && shouldShowRequestPermissionRationale(Manifest.permission.BLUETOOTH_CONNECT)) {
            MaterialAlertDialogBuilder(requireContext())
                .setMessage(R.string.permission_bluetooth_rationale)
                .setPositiveButton("OK") { _, _ -> launcher.launch(Manifest.permission.BLUETOOTH_CONNECT) }
                .setNegativeButton("Cancel", null)
                .create()
                .show()
        } else
            launcher.launch(Manifest.permission.BLUETOOTH_CONNECT)
    }

    private fun requestNotificationPermission(skipRationale: Boolean) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU)
            return
        if (skipRationale)
            notificationsRequestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        else {
            MaterialAlertDialogBuilder(requireContext())
                .setMessage(R.string.permission_notification_rationale)
                .setPositiveButton("OK") { _, _ ->
                    notificationsRequestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
                .setNegativeButton("Cancel", null)
                .create()
                .show()
        }
    }

    private val bluetoothRequestPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        if (isGranted) {
            Log.d(MainActivity.TAG, "Bluetooth permission granted.")
            checkBluetoothPermissionState()
        } else {
            Log.w(MainActivity.TAG, "Bluetooth permission denied.")
        }
    }

    private val pairAfterBluetoothRequestPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        if (isGranted) {
            Log.d(MainActivity.TAG, "Bluetooth permission granted.")
            checkBluetoothPermissionState()
            executePair()
        } else {
            Log.w(MainActivity.TAG, "Bluetooth permission denied.")
        }
    }

    private val notificationsRequestPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        if (isGranted) {
            Log.d(MainActivity.TAG, "Notifications permission granted.")
            checkNotificationPermissionState()
        } else {
            Log.w(MainActivity.TAG, "Notifications permission denied.")
        }
    }


    private fun setupCustomButtonList(customButtonListFlow: MutableStateFlow<List<CameraAction>?>) {
        val customButtonsList = binding.customButtonsList

        adapter = CustomButtonRecyclerViewAdapter(customButtonListFlow, this, this)
        customButtonsList.adapter = adapter

        val callback = object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN or ItemTouchHelper.START or ItemTouchHelper.END,
            ItemTouchHelper.START or ItemTouchHelper.END
        ) {
            override fun onMove(
                recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder
            ): Boolean {
                val from = viewHolder.adapterPosition
                val to = target.adapterPosition
                adapter.moveItem(from, to)
                return true
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                adapter.removeItem(viewHolder.adapterPosition)
            }

            override fun onSelectedChanged(viewHolder: RecyclerView.ViewHolder?, actionState: Int) {
                super.onSelectedChanged(viewHolder, actionState)
                if (actionState == ACTION_STATE_DRAG) {
                    viewHolder?.itemView?.alpha = 0.5f
                }
            }

            override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
                super.clearView(recyclerView, viewHolder)
                viewHolder.itemView.alpha = 1.0f
            }
        }

        itemTouchHelper = ItemTouchHelper(callback).apply {
            attachToRecyclerView(customButtonsList)
        }
    }

    override fun startDragging(viewHolder: RecyclerView.ViewHolder) {
        itemTouchHelper?.startDrag(viewHolder)
    }

    override fun itemTouched(index: Int, oldCameraAction: CameraAction) {
        val cameraActionPicker = CameraActionPicker.newInstance(index, oldCameraAction, showDelete = true)
        cameraActionPicker.show(childFragmentManager, null)
    }

    private fun addCustomButton() {
        val cameraActionPicker = CameraActionPicker()
        cameraActionPicker.show(childFragmentManager, null)
    }

    override fun onConfirmCameraActionPicker(index: Int, cameraAction: CameraAction) {
        adapter.updateItem(index, cameraAction)
    }

    override fun onCancelCameraActionPicker() {
    }

    override fun onDeleteCameraActionPicker(index: Int) {
        adapter.removeItem(index)
    }

    fun openURL(target: String) {
        val uri = Uri.parse(target)
        val intent = Intent(Intent.ACTION_VIEW, uri)
        startActivity(intent)
    }
}