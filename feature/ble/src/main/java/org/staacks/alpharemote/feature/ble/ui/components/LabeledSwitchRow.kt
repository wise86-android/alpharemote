package org.staacks.alpharemote.feature.ble.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import org.staacks.alpharemote.core.ui.theme.BluetoothRemoteForSonyCamerasTheme

/**
 * A full-width row with a label on the left and a switch on the right.
 */
@Composable
fun LabeledSwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
        // Semantics group keeping label and switch together, so the switch stays identifiable
        // by its label when several rows share the same parent.
        modifier = modifier.fillMaxWidth().semantics {},
    ) {
        Text(text = label)
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun LabeledSwitchRowPreview() {
    BluetoothRemoteForSonyCamerasTheme {
        LabeledSwitchRow(
            label = "Enable feature",
            checked = true,
            onCheckedChange = {},
        )
    }
}
