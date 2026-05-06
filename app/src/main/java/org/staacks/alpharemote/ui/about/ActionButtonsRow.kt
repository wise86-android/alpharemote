package org.staacks.alpharemote.ui.about

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.staacks.alpharemote.R
import org.staacks.alpharemote.ui.theme.BluetoothRemoteForSonyCamerasTheme

data class ActionButtonInfo(
    val text: String,
    val icon: Any, // Can be @DrawableRes Int or ImageVector
    val url: String,
    val onClick: (String) -> Unit
)

@Composable
fun ActionButtonsRow(buttons: List<ActionButtonInfo>) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceAround,
        verticalAlignment = Alignment.CenterVertically
    ) {
        buttons.forEach { buttonInfo ->
            TextButton(
                onClick = { buttonInfo.onClick(buttonInfo.url) }
            ) {
                when (val icon = buttonInfo.icon) {
                    is Int -> Icon(
                        painter = painterResource(id = icon),
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    is ImageVector -> Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                Spacer(Modifier.size(8.dp))
                Text(text = buttonInfo.text)
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ActionButtonsRowPreview() {
    BluetoothRemoteForSonyCamerasTheme {
        ActionButtonsRow(
            buttons = listOf(
                ActionButtonInfo(
                    text = "Blog",
                    icon = R.drawable.ca_stop, // Just a placeholder for preview
                    url = "https://example.com",
                    onClick = {}
                )
            )
        )
    }
}

