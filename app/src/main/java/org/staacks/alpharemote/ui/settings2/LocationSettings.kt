package org.staacks.alpharemote.ui.settings2

import android.Manifest
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import org.staacks.alpharemote.ui.settings.RequireLocationPermissionDialog
import androidx.compose.ui.tooling.preview.Preview
import org.staacks.alpharemote.ui.theme.BluetoothRemoteForSonyCamerasTheme
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.Modifier

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun LocationSettings(checked: Boolean, onCheckedChange: (Boolean) -> Unit){
    val backgroundLocationPermissionState = rememberMultiplePermissionsState(listOf(
        Manifest.permission.ACCESS_BACKGROUND_LOCATION,
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION))


        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Send Location")
                Switch(
                    checked = checked,
                    onCheckedChange = onCheckedChange
                )
            }
                if( checked && !backgroundLocationPermissionState.allPermissionsGranted){
                    RequireLocationPermissionDialog()
                    Text("Background Location Permission Not Granted, it will not work!")
                }
            }


    }

@Preview(showBackground = true)
@Composable
fun LocationSettingsPreviewOn() {
    BluetoothRemoteForSonyCamerasTheme {
        LocationSettings(checked = true, onCheckedChange = {})
    }
}

@Preview(showBackground = true)
@Composable
fun LocationSettingsPreviewOff() {
    BluetoothRemoteForSonyCamerasTheme {
        LocationSettings(checked = false, onCheckedChange = {})
    }
}
