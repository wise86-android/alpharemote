package org.staacks.alpharemote.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.staacks.alpharemote.ui.theme.BluetoothRemoteForSonyCamerasTheme

/**
 * A warning about a missing permission with a button to request it.
 */
@Composable
fun PermissionWarning(
    warningText: String,
    buttonText: String,
    onRequestClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = warningText,
            color = MaterialTheme.colorScheme.error,
        )
        TextButton(onClick = onRequestClick) {
            Text(text = buttonText)
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun PermissionWarningPreview() {
    BluetoothRemoteForSonyCamerasTheme {
        PermissionWarning(
            warningText = "The permission is missing.",
            buttonText = "Grant permission",
            onRequestClick = {},
        )
    }
}
