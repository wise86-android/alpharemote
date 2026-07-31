package org.staacks.alpharemote.feature.dof

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview

/**
 * A continuous slider with its label and the current value spelled out above it.
 */
@Composable
internal fun ControlSlider(
    label: String,
    valueLabel: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    range: ClosedFloatingPointRange<Float>,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(label, style = MaterialTheme.typography.labelLarge)
            Text(valueLabel, style = MaterialTheme.typography.bodyMedium)
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = range,
            modifier = Modifier
                .padding(horizontal = SliderTrackInset)
                .systemGestureExclusion()
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ControlSliderPreview() {
    MaterialTheme {
        ControlSlider(
            label = "Focal length",
            valueLabel = "50 mm",
            value = 50f,
            onValueChange = {},
            range = FOCAL_LENGTH_RANGE_MM
        )
    }
}
