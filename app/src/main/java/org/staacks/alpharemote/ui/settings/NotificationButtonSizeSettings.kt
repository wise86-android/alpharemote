package org.staacks.alpharemote.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import org.staacks.alpharemote.R
import org.staacks.alpharemote.ui.theme.BluetoothRemoteForSonyCamerasTheme

@Composable
fun NotificationButtonSizeSettings(
    selectedIndex: Int,
    maxIndex: Int,
    onIndexChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val normalizedIndex = selectedIndex.coerceIn(0, maxIndex)

    Column(modifier = modifier) {
        Text(
            text = stringResource(R.string.settings_button_size),
            style = MaterialTheme.typography.headlineSmall,
        )
        Text(text = stringResource(R.string.settings_button_size_explanation))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
        ) {
            Text(
                text = stringResource(R.string.settings_button_size_smaller),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.clickable {
                    onIndexChange((normalizedIndex - 1).coerceAtLeast(0))
                },
            )

            Slider(
                value = normalizedIndex.toFloat(),
                onValueChange = { value -> onIndexChange(value.roundToInt()) },
                valueRange = 0f..maxIndex.toFloat(),
                steps = (maxIndex - 1).coerceAtLeast(0),
                modifier = Modifier.weight(1f)
            )

            Text(
                text = stringResource(R.string.settings_button_size_larger),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.clickable {
                    onIndexChange((normalizedIndex + 1).coerceAtMost(maxIndex))
                },
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun NotificationButtonSizeSettingsPreview() {
    BluetoothRemoteForSonyCamerasTheme {
        NotificationButtonSizeSettings(
            selectedIndex = 3,
            maxIndex = 6,
            onIndexChange = {},
        )
    }
}


