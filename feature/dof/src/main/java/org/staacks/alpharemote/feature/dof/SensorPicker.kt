package org.staacks.alpharemote.feature.dof

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview

/**
 * Picks the sensor format. The formats are few enough to show side by side, so every option stays
 * visible and is one tap away rather than hidden behind a menu.
 */
@Composable
internal fun SensorPicker(
    selected: SensorType,
    onSensorChange: (SensorType) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.dof_sensor_size),
            style = MaterialTheme.typography.labelLarge
        )
        Spacer(modifier = Modifier.height(LabelSpacing))
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            SensorType.entries.forEachIndexed { index, sensor ->
                SegmentedButton(
                    selected = sensor == selected,
                    onClick = { onSensorChange(sensor) },
                    shape = SegmentedButtonDefaults.itemShape(
                        index = index,
                        count = SensorType.entries.size
                    ),
                    // The check icon would crowd out the labels at three segments wide; the
                    // selected container colour already carries the state.
                    icon = {},
                    label = {
                        Text(
                            text = stringResource(sensor.labelRes),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                )
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 360)
@Composable
private fun SensorPickerPreview() {
    MaterialTheme {
        SensorPicker(selected = SensorType.APS_C, onSensorChange = {})
    }
}
