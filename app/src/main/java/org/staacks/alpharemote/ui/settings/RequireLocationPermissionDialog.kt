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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import org.staacks.alpharemote.R
@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun RequireLocationPermissionDialog(){
    val backgroundLocationPermissionState = rememberPermissionState(
        android.Manifest.permission.ACCESS_BACKGROUND_LOCATION)
    val locationPermission = rememberPermissionState(
    android.Manifest.permission.ACCESS_FINE_LOCATION)

    var showPermissionDialog by remember { mutableStateOf(true) }

    //all permission are granted, notting to display
    if(backgroundLocationPermissionState.status.isGranted && locationPermission.status.isGranted){
        return
    }

    //ask for location permission
    if(!locationPermission.status.isGranted && showPermissionDialog){
        RequirePermissionDialog(
            hideDialog = {showPermissionDialog = false},
            requestPermission = locationPermission::launchPermissionRequest
        ) {
            Text(stringResource(R.string.permission_dialog_location_rationale))
        }
    }
    //if location permission is fine, ask for background location permission
    if(locationPermission.status.isGranted && !backgroundLocationPermissionState.status.isGranted
        && showPermissionDialog){
        RequirePermissionDialog(
            hideDialog = {showPermissionDialog = false},
            requestPermission = backgroundLocationPermissionState::launchPermissionRequest
        ) {
            Text(stringResource(R.string.permisison_dialog_background_location_rationale))
        }
    }
}

@Composable
fun RequirePermissionDialog(hideDialog: () -> Unit,requestPermission: () -> Unit,body:@Composable ()-> Unit){
    AlertDialog(
        icon = {Icon(Icons.Outlined.LocationOn,"",
            tint = MaterialTheme.colorScheme.onBackground)},
        title = {
            Text(stringResource(R.string.permission_dialog_title))
        },
        text = body,
        onDismissRequest = hideDialog,
        confirmButton = {
            TextButton(
                onClick = requestPermission
            ) {
                Text(stringResource(R.string.permission_dialog_confirm_button))
            }
        },
    )
}

@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun RequirePermissionDialogPreview(){
    RequirePermissionDialog({},{}) {
        Text("why requiring a permission")
    }
}