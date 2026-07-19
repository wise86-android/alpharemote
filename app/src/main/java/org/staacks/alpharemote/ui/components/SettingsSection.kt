package org.staacks.alpharemote.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.staacks.alpharemote.ui.theme.BluetoothRemoteForSonyCamerasTheme

/**
 * A titled section as used on the settings screen: headline, optional explanation text and
 * the section content below.
 */
@Composable
fun SettingsSection(
    title: String,
    modifier: Modifier = Modifier,
    description: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
        )
        description?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        content()
    }
}

@Preview(showBackground = true)
@Composable
private fun SettingsSectionPreview() {
    BluetoothRemoteForSonyCamerasTheme {
        SettingsSection(
            title = "Section title",
            description = "Explanation of what this section configures.",
        ) {
            Text(text = "Section content")
        }
    }
}
