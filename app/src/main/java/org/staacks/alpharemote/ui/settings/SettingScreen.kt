package org.staacks.alpharemote.ui.settings

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.location.LocationManager
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.content.ContextCompat
import org.staacks.alpharemote.R
import org.staacks.alpharemote.camera.CameraAction
import org.staacks.alpharemote.camera.CameraActionPreset
import org.staacks.alpharemote.ui.theme.BluetoothRemoteForSonyCamerasTheme
import org.staacks.alpharemote.ui.theme.FragmentMargin

@Composable
fun SettingScreen(
    settingsViewModel: SettingsViewModel,
    onPairRequested: () -> Unit,
    onUnpairRequested: () -> Unit,
    onAddCustomButtonRequested: () -> Unit,
    onHelpConnectionRequested: () -> Unit,
    onHelpCustomButtonsRequested: () -> Unit,
    onEditCustomButton: (Int, CameraAction) -> Unit,
    onOpenUrl: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val sectionSpacing = dimensionResource(R.dimen.headline_margin_top)

    val uiState by settingsViewModel.uiState.collectAsState()
    val updateCameraLocation by settingsViewModel.updateCameraLocation.collectAsState(false)
    val customButtons by settingsViewModel.customButtonListFlow.collectAsState()
    val selectedButtonScaleIndex by settingsViewModel.buttonScaleIndex.collectAsState(0)
    val broadcastControlEnabled by settingsViewModel.broadcastControl.collectAsState(false)

    val broadcastDocumentationUrl = stringResource(R.string.settings_broadcast_control_more_url)

    // Helper functions moved from fragment
    fun checkAssociations() {
        val address = CompanionDeviceHelper.getAssociation(context).firstOrNull()
        val isAssociated = address != null
        val isBonded = isAssociated && try {
            val adapter = ContextCompat.getSystemService(context, BluetoothManager::class.java)?.adapter
            adapter?.getRemoteDevice(address)?.bondState == BluetoothDevice.BOND_BONDED
        } catch (_: SecurityException) {
            false
        }
        settingsViewModel.updateAssociationState(address, isAssociated, isBonded)
    }

    fun checkBluetoothState() {
        val adapter = ContextCompat.getSystemService(context, BluetoothManager::class.java)?.adapter
        val enabled = adapter?.state == BluetoothAdapter.STATE_ON
        settingsViewModel.updateBluetoothState(enabled)
    }

    fun checkLocationServiceState() {
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val bleScanning = try {
            Settings.Global.getInt(context.contentResolver, "ble_scan_always_enabled") == 1
        } catch (_: Exception) {
            true
        }
        settingsViewModel.updateLocationServiceState(locationManager.isLocationEnabled, bleScanning)
    }

    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                when (intent?.action) {
                    BluetoothDevice.ACTION_BOND_STATE_CHANGED -> checkAssociations()
                    BluetoothAdapter.ACTION_STATE_CHANGED -> checkBluetoothState()
                    LocationManager.MODE_CHANGED_ACTION -> checkLocationServiceState()
                }
            }
        }
        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
            addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
            addAction(LocationManager.MODE_CHANGED_ACTION)
        }
        context.registerReceiver(receiver, filter)
        
        // Initial checks
        checkBluetoothState()
        checkLocationServiceState()
        checkAssociations()

        onDispose {
            context.unregisterReceiver(receiver)
        }
    }

    LaunchedEffect(settingsViewModel) {
        settingsViewModel.uiAction.collect { action ->
            when (action) {
                SettingsViewModel.SettingsUIAction.PAIR -> onPairRequested()
                SettingsViewModel.SettingsUIAction.UNPAIR -> onUnpairRequested()
                SettingsViewModel.SettingsUIAction.ADD_CUSTOM_BUTTON -> onAddCustomButtonRequested()
                SettingsViewModel.SettingsUIAction.HELP_CONNECTION -> onHelpConnectionRequested()
                SettingsViewModel.SettingsUIAction.HELP_CUSTOM_BUTTONS -> onHelpCustomButtonsRequested()
            }
        }
    }

    SettingScreenContent(
        sectionSpacing = sectionSpacing,
        uiState = uiState,
        updateCameraLocation = updateCameraLocation,
        customButtons = customButtons,
        selectedButtonScaleIndex = selectedButtonScaleIndex,
        maxButtonScaleIndex = settingsViewModel.buttonScaleSteps.lastIndex,
        broadcastControlEnabled = broadcastControlEnabled,
        onPairClick = settingsViewModel::pair,
        onUnpairClick = settingsViewModel::unpair,
        onHelpConnectionClick = settingsViewModel::helpConnection,
        onLocationUpdatesCheckedChange = settingsViewModel::setUpdateCameraLocation,
        onAddCustomButtonClick = settingsViewModel::addCustomButton,
        onHelpCustomButtonsClick = settingsViewModel::helpCustomButtons,
        onEditCustomButton = onEditCustomButton,
        onMoveCustomButton = settingsViewModel::moveCustomButton,
        onDeleteCustomButton = settingsViewModel::removeCustomButton,
        onButtonScaleIndexChange = settingsViewModel::setButtonScaleIndex,
        onBroadcastControlCheckedChange = settingsViewModel::setBroadcastControl,
        onBroadcastMoreClick = { onOpenUrl(broadcastDocumentationUrl) },
        modifier = modifier,
    )
}

@Composable
private fun SettingScreenContent(
    sectionSpacing: androidx.compose.ui.unit.Dp,
    uiState: SettingsViewModel.SettingsUIState,
    updateCameraLocation: Boolean,
    customButtons: List<CameraAction>,
    selectedButtonScaleIndex: Int,
    maxButtonScaleIndex: Int,
    broadcastControlEnabled: Boolean,
    onPairClick: () -> Unit,
    onUnpairClick: () -> Unit,
    onHelpConnectionClick: () -> Unit,
    onLocationUpdatesCheckedChange: (Boolean) -> Unit,
    onAddCustomButtonClick: () -> Unit,
    onHelpCustomButtonsClick: () -> Unit,
    onEditCustomButton: (Int, CameraAction) -> Unit,
    onMoveCustomButton: (Int, Int) -> Unit,
    onDeleteCustomButton: (Int) -> Unit,
    onButtonScaleIndexChange: (Int) -> Unit,
    onBroadcastControlCheckedChange: (Boolean) -> Unit,
    onBroadcastMoreClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface {
        Column(
            modifier = modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .verticalScroll(rememberScrollState())
                .padding(FragmentMargin),
            verticalArrangement = Arrangement.spacedBy(sectionSpacing),
        ) {
            Text(
                text = stringResource(R.string.title_settings),
                style = MaterialTheme.typography.headlineMedium,
            )

            CameraSettingsSection(
                state = uiState,
                onPairClick = onPairClick,
                onUnpairClick = onUnpairClick,
                onHelpClick = onHelpConnectionClick,
            )

            MissingBluetoothPermissionSettings()

            MissingNotificationPermissionSettings()

            MissingLocationPermissionSettings(
                locationUpdatesEnabled = updateCameraLocation,
            )

            LocationSettings(
                checked = updateCameraLocation,
                onCheckedChange = onLocationUpdatesCheckedChange,
            )

            CustomButtonsSettingsSection(
                buttons = customButtons,
                onAddClick = onAddCustomButtonClick,
                onHelpClick = onHelpCustomButtonsClick,
                onEditClick = onEditCustomButton,
                onMove = onMoveCustomButton,
                onDelete = onDeleteCustomButton,
            )

            NotificationButtonSizeSettings(
                selectedIndex = selectedButtonScaleIndex,
                maxIndex = maxButtonScaleIndex,
                onIndexChange = onButtonScaleIndexChange,
            )

            BroadcastControlSettings(
                enabled = broadcastControlEnabled,
                onCheckedChange = onBroadcastControlCheckedChange,
                onMoreClick = onBroadcastMoreClick,
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun SettingScreenPreview() {
    BluetoothRemoteForSonyCamerasTheme {
        SettingScreenContent(
            sectionSpacing = dimensionResource(R.dimen.headline_margin_top),
            uiState = SettingsViewModel.SettingsUIState(
                cameraState = SettingsViewModel.SettingsUICameraState.CONNECTED,
                cameraError = null,
                cameraName = "Alpha 1",
                bluetoothEnabled = true,
                locationServiceEnabled = true,
                bleScanningEnabled = true,
            ),
            updateCameraLocation = true,
            customButtons = listOf(
                CameraAction(false, null, null, null, CameraActionPreset.TRIGGER_ONCE),
                CameraAction(false, 3.0f, null, null, CameraActionPreset.TRIGGER_ONCE),
                CameraAction(false, null, null, null, CameraActionPreset.RECORD),
            ),
            selectedButtonScaleIndex = 3,
            maxButtonScaleIndex = 6,
            broadcastControlEnabled = true,
            onPairClick = {},
            onUnpairClick = {},
            onHelpConnectionClick = {},
            onLocationUpdatesCheckedChange = {},
            onAddCustomButtonClick = {},
            onHelpCustomButtonsClick = {},
            onEditCustomButton = { _, _ -> },
            onMoveCustomButton = { _, _ -> },
            onDeleteCustomButton = {},
            onButtonScaleIndexChange = {},
            onBroadcastControlCheckedChange = {},
            onBroadcastMoreClick = {},
        )
    }
}
