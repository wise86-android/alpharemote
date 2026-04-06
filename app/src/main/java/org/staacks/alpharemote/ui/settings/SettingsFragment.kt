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
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.dimensionResource
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.Lifecycle
import com.google.android.material.dialog.MaterialAlertDialogBuilder
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
import org.staacks.alpharemote.utils.hasNotificationPermission
import androidx.core.net.toUri

class SettingsFragment : Fragment(), CameraActionPickerListener {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    private lateinit var settingsViewModel: SettingsViewModel

    val onDeviceFoundLauncher = registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { activityResult  ->
        Log.d(MainActivity.TAG, "Activity Result: $activityResult")
        if (activityResult.resultCode == Activity.RESULT_OK) {
            val scanResult: ScanResult? = extractScanResult(activityResult.data)
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
        settingsViewModel = ViewModelProvider(this)[SettingsViewModel::class.java]

        _binding = FragmentSettingsBinding.inflate(inflater, container, false)

        binding.composePermissionRequest.apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                // In Compose world
                BluetoothRemoteForSonyCamerasTheme {
                    val uiState by settingsViewModel.uiState.collectAsState()
                    val updateCameraLocation by settingsViewModel.updateCameraLocation.collectAsState(false)
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                    ) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(dimensionResource(R.dimen.headline_margin_top)),
                        ) {
                            CameraSettingsSection(
                                state = uiState,
                                onPairClick = settingsViewModel::pair,
                                onUnpairClick = settingsViewModel::unpair,
                                onHelpClick = settingsViewModel::helpConnection,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            MissingBluetoothPermissionSettings(
                                modifier = Modifier.fillMaxWidth(),
                            )
                            MissingNotificationPermissionSettings(
                                modifier = Modifier.fillMaxWidth(),
                            )
                            MissingLocationPermissionSettings(
                                locationUpdatesEnabled = updateCameraLocation,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            LocationSettings(
                                checked = updateCameraLocation,
                                onCheckedChange = settingsViewModel::setUpdateCameraLocation,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            }
        }

        binding.composeCustomButtons.apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                BluetoothRemoteForSonyCamerasTheme {
                    val customButtons by settingsViewModel.customButtonListFlow.collectAsState()
                    Surface {
                        CustomButtonsSettingsSection(
                            buttons = customButtons,
                            onAddClick = settingsViewModel::addCustomButton,
                            onHelpClick = settingsViewModel::helpCustomButtons,
                            onEditClick = ::openCustomButtonEditor,
                            onMove = settingsViewModel::moveCustomButton,
                            onDelete = settingsViewModel::removeCustomButton,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }

        binding.composeSettingsControls.apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                BluetoothRemoteForSonyCamerasTheme {
                    val selectedButtonScaleIndex by settingsViewModel.buttonScaleIndex.collectAsState(0)
                    val broadcastControlEnabled by settingsViewModel.broadcastControl.collectAsState(false)
                    Surface {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(dimensionResource(R.dimen.headline_margin_top)),
                        ) {
                            NotificationButtonSizeSettings(
                                selectedIndex = selectedButtonScaleIndex,
                                maxIndex = settingsViewModel.buttonScaleSteps.lastIndex,
                                onIndexChange = settingsViewModel::setButtonScaleIndex,
                                modifier = Modifier.fillMaxWidth(),
                            )

                            BroadcastControlSettings(
                                enabled = broadcastControlEnabled,
                                onCheckedChange = settingsViewModel::setBroadcastControl,
                                onMoreClick = { openURL(getString(R.string.settings_broadcast_control_more_url)) },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
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
                            SettingsViewModel.SettingsUIAction.ADD_CUSTOM_BUTTON -> addCustomButton()
                            SettingsViewModel.SettingsUIAction.HELP_CONNECTION ->
                                HelpDialogFragment.newInstance(
                                    R.string.help_settings_connection_troubleshooting_title,
                                    R.string.help_settings_connection_troubleshooting_text
                                ).show(childFragmentManager, null)
                            SettingsViewModel.SettingsUIAction.HELP_CUSTOM_BUTTONS ->
                                HelpDialogFragment.newInstance(
                                    R.string.help_settings_custom_buttons_title,
                                    R.string.help_settings_custom_buttons_text
                                ).show(childFragmentManager, null)
                        }
                    }
                }
            }
        }

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


        checkBluetoothState()
        checkLocationServiceState()
        checkAssociations()

        return binding.root
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
            .setPositiveButton(R.string.settings_camera_remove_confirm) { _, _ ->
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
        return hasBluetoothPermission(requireContext())
    }

    private fun checkNotificationPermissionState(): Boolean {
        return hasNotificationPermission(requireContext())
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

    private val pairAfterBluetoothRequestPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        if (isGranted) {
            Log.d(MainActivity.TAG, "Bluetooth permission granted.")
            executePair()
        } else {
            Log.w(MainActivity.TAG, "Bluetooth permission denied.")
        }
    }

    private val notificationsRequestPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        if (isGranted) {
            Log.d(MainActivity.TAG, "Notifications permission granted.")
        } else {
            Log.w(MainActivity.TAG, "Notifications permission denied.")
        }
    }


    private fun openCustomButtonEditor(index: Int, oldCameraAction: CameraAction) {
        val cameraActionPicker = CameraActionPicker.newInstance(index, oldCameraAction, showDelete = true)
        cameraActionPicker.show(childFragmentManager, null)
    }

    private fun addCustomButton() {
        val cameraActionPicker = CameraActionPicker()
        cameraActionPicker.show(childFragmentManager, null)
    }

    override fun onConfirmCameraActionPicker(index: Int, cameraAction: CameraAction) {
        settingsViewModel.updateCustomButton(index, cameraAction)
    }

    override fun onCancelCameraActionPicker() {
    }

    override fun onDeleteCameraActionPicker(index: Int) {
        settingsViewModel.removeCustomButton(index)
    }

    fun openURL(target: String) {
        val uri = target.toUri()
        val intent = Intent(Intent.ACTION_VIEW, uri)
        startActivity(intent)
    }

    @Suppress("DEPRECATION")
    private fun extractScanResult(intent: Intent?): ScanResult? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent?.getParcelableExtra(CompanionDeviceManager.EXTRA_DEVICE, ScanResult::class.java)
        } else {
            intent?.getParcelableExtra(CompanionDeviceManager.EXTRA_DEVICE)
        }
    }
}