package org.staacks.alpharemote.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import org.staacks.alpharemote.R
import org.staacks.alpharemote.ui.theme.BluetoothRemoteForSonyCamerasTheme


@Composable
fun LocationSettings(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(modifier = modifier) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(stringResource(R.string.settings_location_send))
                Switch(
                    checked = checked,
                    onCheckedChange = onCheckedChange,
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun LocationSettingsDisabledPreview() {
    BluetoothRemoteForSonyCamerasTheme {
        LocationSettings(
            checked = false,
            onCheckedChange = {},
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun LocationSettingsEnabledPreview() {
    BluetoothRemoteForSonyCamerasTheme {
        LocationSettings(
            checked = true,
            onCheckedChange = {},
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

