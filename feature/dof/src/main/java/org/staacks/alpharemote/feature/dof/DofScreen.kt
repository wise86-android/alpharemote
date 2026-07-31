package org.staacks.alpharemote.feature.dof

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * Depth of field calculator. Self-contained: it needs no camera connection, so it works whether or
 * not a camera is in range.
 *
 * The pieces it assembles live in their own files: [PhotographyGraphic], [ControlSlider],
 * [DiscreteSlider], [SensorPicker] and [DofResultCard].
 */
@Composable
fun DofScreen(viewModel: DofViewModel) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    DofScreenContent(
        input = uiState.input,
        result = uiState.result,
        onDistanceChange = viewModel::updateDistance,
        onFocalLengthChange = viewModel::updateFocalLength,
        onApertureChange = viewModel::updateAperture,
        onSensorChange = viewModel::updateSensor
    )
}

@Composable
internal fun DofScreenContent(
    input: DofSettings.DofInput,
    result: DofResult,
    onDistanceChange: (Float) -> Unit,
    onFocalLengthChange: (Float) -> Unit,
    onApertureChange: (Float) -> Unit,
    onSensorChange: (SensorType) -> Unit
) {
    val context = LocalContext.current

    Surface {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .verticalScroll(rememberScrollState())
                .padding(ScreenMargin),
            verticalArrangement = Arrangement.spacedBy(ItemSpacing)
        ) {
            Text(
                text = stringResource(R.string.dof_title),
                style = MaterialTheme.typography.displaySmall
            )

            PhotographyGraphic(
                distanceMm = input.distanceMeters * 1000f,
                nearLimitMm = result.nearLimitMm,
                farLimitMm = result.farLimitMm,
                focalLengthMm = input.focalLengthMm,
                aperture = input.aperture,
                verticalFov = result.verticalFov,
                onDistanceChange = onDistanceChange
            )

            // The numbers sit with the diagram they explain; the controls follow underneath.
            DofResultCard(result = result)

            Spacer(modifier = Modifier.height(SectionSpacing - ItemSpacing))

            ControlSlider(
                label = stringResource(R.string.dof_subject_distance),
                valueLabel = context.formatDistance(input.distanceMeters * 1000f),
                value = input.distanceMeters,
                onValueChange = onDistanceChange,
                range = DISTANCE_RANGE_M
            )

            ControlSlider(
                label = stringResource(R.string.dof_focal_length),
                valueLabel = context.formatFocalLength(input.focalLengthMm),
                value = input.focalLengthMm,
                onValueChange = onFocalLengthChange,
                range = FOCAL_LENGTH_RANGE_MM
            )

            DiscreteSlider(
                label = stringResource(R.string.dof_aperture),
                valueLabel = context.formatAperture(input.aperture),
                value = input.aperture,
                values = APERTURES,
                onValueChange = onApertureChange
            )

            SensorPicker(selected = input.sensor, onSensorChange = onSensorChange)
        }
    }
}

@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_NO)
@Composable
private fun DofScreenContentPreviewLight() {
    DofScreenContentPreview()
}

@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun DofScreenContentPreviewDark() {
    DofScreenContentPreview()
}

@Composable
private fun DofScreenContentPreview() {
    // The host app supplies its own theme at runtime; previews only need a Material 3 baseline.
    MaterialTheme {
        val input = DofSettings.DEFAULT_INPUT
        DofScreenContent(
            input = input,
            result = calculateDof(
                input.distanceMeters * 1000f,
                input.focalLengthMm,
                input.aperture,
                input.sensor
            ),
            onDistanceChange = {},
            onFocalLengthChange = {},
            onApertureChange = {},
            onSensorChange = {}
        )
    }
}
