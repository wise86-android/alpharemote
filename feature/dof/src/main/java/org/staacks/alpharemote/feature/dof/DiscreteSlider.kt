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
import kotlin.math.roundToInt

/**
 * A slider that snaps to a fixed list of values, such as the aperture scale. The slider itself runs
 * over list indices; only values from [values] are ever reported back.
 */
@Composable
internal fun DiscreteSlider(
    label: String,
    valueLabel: String,
    value: Float,
    values: List<Float>,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val index = values.indexOf(value).coerceAtLeast(0)
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(label, style = MaterialTheme.typography.labelLarge)
            Text(valueLabel, style = MaterialTheme.typography.bodyMedium)
        }
        Slider(
            value = index.toFloat(),
            onValueChange = { onValueChange(values[it.roundToInt().coerceIn(values.indices)]) },
            valueRange = 0f..(values.size - 1).toFloat(),
            steps = if (values.size > 1) values.size - 2 else 0,
            modifier = Modifier
                .padding(horizontal = SliderTrackInset)
                .systemGestureExclusion()
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun DiscreteSliderPreview() {
    MaterialTheme {
        DiscreteSlider(
            label = "Aperture",
            valueLabel = "f/5.6",
            value = 5.6f,
            values = APERTURES,
            onValueChange = {}
        )
    }
}
