package org.staacks.alpharemote.ui.settings

import android.content.res.Configuration
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import org.staacks.alpharemote.R

enum class LocationPermissionStep {
    PRECISE,
    BACKGROUND,
}

@Composable
fun RequireLocationPermissionDialog(
    step: LocationPermissionStep,
    onDismissRequest: () -> Unit,
    onConfirmRequest: () -> Unit,
) {
    RequirePermissionDialog(
        hideDialog = onDismissRequest,
        requestPermission = onConfirmRequest,
    ) {
        when (step) {
            LocationPermissionStep.PRECISE -> Text(stringResource(R.string.permission_dialog_location_precise_rationale))
            LocationPermissionStep.BACKGROUND -> Text(stringResource(R.string.permission_dialog_background_location_rationale))
        }
    }
}

@Composable
fun RequirePermissionDialog(
    hideDialog: () -> Unit,
    requestPermission: () -> Unit,
    body: @Composable () -> Unit,
) {
    AlertDialog(
        icon = {
            Icon(
                imageVector = Icons.Outlined.LocationOn,
                contentDescription = "",
                tint = MaterialTheme.colorScheme.onBackground,
            )
        },
        title = {
            Text(stringResource(R.string.permission_dialog_title))
        },
        text = body,
        onDismissRequest = hideDialog,
        confirmButton = {
            TextButton(
                onClick = requestPermission,
            ) {
                Text(stringResource(R.string.permission_dialog_confirm_button))
            }
        },
        dismissButton = {
            TextButton(onClick = hideDialog) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}

@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun RequirePermissionDialogPreview() {
    RequireLocationPermissionDialog(
        step = LocationPermissionStep.PRECISE,
        onDismissRequest = {},
        onConfirmRequest = {},
    )
}

@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun RequirePermissionDialogBackgroundPreview() {
    RequireLocationPermissionDialog(
        step = LocationPermissionStep.BACKGROUND,
        onDismissRequest = {},
        onConfirmRequest = {},
    )
}