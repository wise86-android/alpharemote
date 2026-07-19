package org.staacks.alpharemote.ui.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Help
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import org.staacks.alpharemote.R
import org.staacks.alpharemote.ui.components.LabeledSwitchRow
import org.staacks.alpharemote.ui.components.SettingsSection
import org.staacks.alpharemote.ui.theme.BluetoothRemoteForSonyCamerasTheme

@Composable
fun BroadcastControlSettings(
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    onMoreClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SettingsSection(
        title = stringResource(R.string.settings_broadcast_control),
        description = stringResource(R.string.settings_broadcast_control_explanation),
        modifier = modifier,
    ) {
        LabeledSwitchRow(
            label = stringResource(R.string.settings_broadcast_control_toggle),
            checked = enabled,
            onCheckedChange = onCheckedChange,
        )

        TextButton(onClick = onMoreClick) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.Help,
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
