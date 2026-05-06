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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.staacks.alpharemote.R
import org.staacks.alpharemote.ui.theme.BluetoothRemoteForSonyCamerasTheme

data class ActionButtonInfo(
    val text: String,
    @DrawableRes val iconResId: Int,
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
                Icon(
                    painter = painterResource(id = buttonInfo.iconResId),
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
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
                    iconResId = R.drawable.baseline_text_snippet_24,
                    url = "https://example.com",
                    onClick = {}
                ),
                ActionButtonInfo(
                    text = "Issues",
                    iconResId = R.drawable.baseline_bug_report_24,
                    url = "https://example.com/issues",
                    onClick = {}
                )
            )
        )
    }
}

