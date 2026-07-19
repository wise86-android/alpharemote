package org.staacks.alpharemote.ui.settings

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import org.staacks.alpharemote.R
import org.staacks.alpharemote.ui.components.PermissionWarning
import org.staacks.alpharemote.ui.theme.BluetoothRemoteForSonyCamerasTheme
import org.staacks.alpharemote.utils.rememberBlePermissionState

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun MissingBluetoothPermissionSettings(
    modifier: Modifier = Modifier,
) {
    val bluetoothPermissionState = rememberBlePermissionState()
    val missingBluetooth = !bluetoothPermissionState.status.isGranted
    if (!missingBluetooth) return

    PermissionWarning(
        warningText = stringResource(R.string.settings_missing_bluetooth_permission_warning),
        buttonText = stringResource(R.string.settings_bluetooth_permission_button),
        onRequestClick = bluetoothPermissionState::launchPermissionRequest,
        modifier = modifier,
    )
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun MissingNotificationPermissionSettings(
    modifier: Modifier = Modifier,
) {
    val notificationPermissionState = rememberPermissionState(android.Manifest.permission.POST_NOTIFICATIONS)
    val missingNotification = !notificationPermissionState.status.isGranted
    if (!missingNotification) return

    PermissionWarning(
        warningText = stringResource(R.string.settings_missing_notification_permission_warning),
        buttonText = stringResource(R.string.settings_notification_permission_button),
        onRequestClick = notificationPermissionState::launchPermissionRequest,
        modifier = modifier,
    )
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun MissingLocationPermissionSettings(
    locationUpdatesEnabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val fineLocationPermission = rememberPermissionState(android.Manifest.permission.ACCESS_FINE_LOCATION)
    val coarseLocationPermission = rememberPermissionState(android.Manifest.permission.ACCESS_COARSE_LOCATION)
    val backgroundLocationPermission = rememberPermissionState(android.Manifest.permission.ACCESS_BACKGROUND_LOCATION)

    val hasForegroundLocation = fineLocationPermission.status.isGranted || coarseLocationPermission.status.isGranted
    val hasBackgroundLocation = backgroundLocationPermission.status.isGranted
    val missingLocation = locationUpdatesEnabled && (!hasForegroundLocation || !hasBackgroundLocation)

    var showPermissionDialog by remember { mutableStateOf(false) }
    val dialogStep = when {
        !hasForegroundLocation -> LocationPermissionStep.PRECISE
        !hasBackgroundLocation -> LocationPermissionStep.BACKGROUND
        else -> null
    }

    LaunchedEffect(missingLocation) {
        if (!missingLocation) showPermissionDialog = false
    }

    if (!missingLocation) return

    PermissionWarning(
        warningText = stringResource(R.string.settings_missing_location_permission_warning),
        buttonText = stringResource(R.string.settings_location_permission_button),
        onRequestClick = { showPermissionDialog = true },
        modifier = modifier,
    )

    if (showPermissionDialog && dialogStep != null) {
        val onConfirmRequest = when (dialogStep) {
            LocationPermissionStep.PRECISE -> fineLocationPermission::launchPermissionRequest
            LocationPermissionStep.BACKGROUND -> backgroundLocationPermission::launchPermissionRequest
        }
        RequireLocationPermissionDialog(
            step = dialogStep,
            onDismissRequest = {
                if (showPermissionDialog) {
                    showPermissionDialog = false
                }
            },
            onConfirmRequest = onConfirmRequest,
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun MissingBluetoothPermissionSettingsPreview() {
    BluetoothRemoteForSonyCamerasTheme {
        MissingBluetoothPermissionSettings(
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun MissingNotificationPermissionSettingsPreview() {
    BluetoothRemoteForSonyCamerasTheme {
        MissingNotificationPermissionSettings(
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun MissingLocationPermissionSettingsPreview() {
    BluetoothRemoteForSonyCamerasTheme {
        MissingLocationPermissionSettings(
            locationUpdatesEnabled = true,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
