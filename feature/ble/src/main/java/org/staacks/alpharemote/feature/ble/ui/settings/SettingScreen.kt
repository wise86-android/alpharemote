package org.staacks.alpharemote.feature.ble.ui.settings
import org.staacks.alpharemote.feature.ble.R

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.staacks.alpharemote.feature.ble.camera.CameraAction
import org.staacks.alpharemote.feature.ble.camera.CameraActionPreset
import org.staacks.alpharemote.feature.ble.ui.components.LabeledSwitchRow
import org.staacks.alpharemote.core.ui.theme.BluetoothRemoteForSonyCamerasTheme
import org.staacks.alpharemote.core.ui.theme.FragmentMargin

@Composable
fun SettingScreen(
    settingsViewModel: SettingsViewModel,
    onEditCustomButton: (Int, CameraAction) -> Unit,
    onOpenUrl: (String) -> Unit,
    onAboutClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val sectionSpacing = dimensionResource(R.dimen.headline_margin_top)

    val uiState by settingsViewModel.uiState.collectAsStateWithLifecycle()
    val updateCameraLocation by settingsViewModel.updateCameraLocation.collectAsStateWithLifecycle(false)
    val customButtons by settingsViewModel.customButtonListFlow.collectAsStateWithLifecycle()
    val selectedButtonScaleIndex by settingsViewModel.buttonScaleIndex.collectAsStateWithLifecycle()
    val broadcastControlEnabled by settingsViewModel.broadcastControl.collectAsStateWithLifecycle()

    val broadcastDocumentationUrl = stringResource(R.string.settings_broadcast_control_more_url)

    SettingScreenContent(
        sectionSpacing = sectionSpacing,
        uiState = uiState,
        updateCameraLocation = updateCameraLocation,
        customButtons = customButtons,
        selectedButtonScaleIndex = selectedButtonScaleIndex,
        maxButtonScaleIndex = settingsViewModel.buttonScaleSteps.lastIndex,
        broadcastControlEnabled = broadcastControlEnabled,
        onPairClick = settingsViewModel::searchNewCamera,
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
        onAboutClick = onAboutClick,
        modifier = modifier,
    )
}

@Composable
internal fun SettingScreenContent(
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
    onAboutClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        Column(
            modifier = modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .verticalScroll(rememberScrollState())
                .padding(FragmentMargin),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Text(
                text = stringResource(R.string.title_settings),
                style = MaterialTheme.typography.headlineMedium,
            )

            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                CameraSettingsSection(
                    state = uiState,
                    onPairClick = onPairClick,
                    onUnpairClick = onUnpairClick,
                    onHelpClick = onHelpConnectionClick,
                )

                MissingBluetoothPermissionSettings()
                MissingNotificationPermissionSettings()
            }

            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                MissingLocationPermissionSettings(
                    locationUpdatesEnabled = updateCameraLocation,
                )

                LabeledSwitchRow(
                    label = stringResource(R.string.settings_location_send),
                    checked = updateCameraLocation,
                    onCheckedChange = onLocationUpdatesCheckedChange,
                )
            }

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

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onAboutClick)
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.settings_about_link),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                )
            }
        }
    }
}

@Preview(showBackground = true,)
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
                CameraAction(false, null, CameraActionPreset.TRIGGER_ONCE),
                CameraAction(false, null, CameraActionPreset.SHUTTER),
                CameraAction(false, null, CameraActionPreset.RECORD),
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
            onAboutClick = {},
        )
    }
}
