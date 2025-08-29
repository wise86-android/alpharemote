package org.staacks.alpharemote.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState


@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun LocationSettings(viewModel: SettingsViewModel){
    val checked by viewModel.updateCameraLocation.collectAsState(false)
    val backgroundLocationPermissionState = rememberMultiplePermissionsState(listOf(
        android.Manifest.permission.ACCESS_BACKGROUND_LOCATION,
        android.Manifest.permission.ACCESS_FINE_LOCATION,
        android.Manifest.permission.ACCESS_COARSE_LOCATION))

    Surface{
        Column {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Send Location")
                Switch(
                    checked = checked,
                    onCheckedChange = {
                        viewModel.setUpdateCameraLocation(it)
                    }
                )
            }
                if( checked && !backgroundLocationPermissionState.allPermissionsGranted){
                    RequireLocationPermissionDialog()
                    Text("Background Location Permission Not Granted, it will not work!")
                }
            }

        }
    }
