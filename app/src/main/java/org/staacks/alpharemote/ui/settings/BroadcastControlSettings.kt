package org.staacks.alpharemote.ui.settings

import android.graphics.drawable.Icon
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.res.stringResource
import org.staacks.alpharemote.R
import org.staacks.alpharemote.ui.theme.BluetoothRemoteForSonyCamerasTheme

@Composable
fun BroadcastControlSettings(
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    onMoreClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(
            text = stringResource(R.string.settings_broadcast_control),
            style = MaterialTheme.typography.headlineSmall,
        )
        Text(text = stringResource(R.string.settings_broadcast_control_explanation))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = stringResource(R.string.settings_broadcast_control_toggle))
            Switch(
                checked = enabled,
                onCheckedChange = onCheckedChange,
            )
        }

        TextButton(onClick = onMoreClick) {
            Icon(
                painter = painterResource(id = R.drawable.baseline_help_24),
                contentDescription = null,
            )
            Text(text = stringResource(R.string.settings_broadcast_control_more_label))
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun BroadcastControlSettingsPreview() {
    BluetoothRemoteForSonyCamerasTheme {
        BroadcastControlSettings(
            enabled = true,
            onCheckedChange = {},
            onMoreClick = {},
        )
    }
}

