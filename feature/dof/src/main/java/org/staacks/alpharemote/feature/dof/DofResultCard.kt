package org.staacks.alpharemote.feature.dof

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import org.staacks.alpharemote.core.ui.theme.FragmentMargin

/**
 * The computed near and far limits and the depth between them, spelled out as numbers next to the
 * diagram's visual answer.
 */
@Composable
internal fun DofResultCard(result: DofResult, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val infinite = stringResource(R.string.dof_infinite)

    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(FragmentMargin),
            verticalArrangement = Arrangement.spacedBy(LabelSpacing)
        ) {
            ResultRow(
                label = stringResource(R.string.dof_near_limit),
                value = context.formatDistance(result.nearLimitMm)
            )
            ResultRow(
                label = stringResource(R.string.dof_far_limit),
                value = context.formatDistance(result.farLimitMm)
            )
            ResultRow(
                label = stringResource(R.string.dof_total),
                value = if (result.farLimitMm.isInfinite()) {
                    infinite
                } else {
                    context.formatDistance(result.farLimitMm - result.nearLimitMm)
                },
                emphasized = true
            )
        }
    }
}

@Composable
private fun ResultRow(label: String, value: String, emphasized: Boolean = false) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium)
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (emphasized) FontWeight.Bold else null
        )
    }
}

@Preview(showBackground = true, widthDp = 360)
@Composable
private fun DofResultCardPreview() {
    MaterialTheme {
        DofResultCard(calculateDof(1800f, 50f, 1.8f, SensorType.FULL_FRAME))
    }
}

@Preview(showBackground = true, widthDp = 360)
@Composable
private fun DofResultCardInfinitePreview() {
    MaterialTheme {
        DofResultCard(calculateDof(5000f, 24f, 8f, SensorType.FULL_FRAME))
    }
}
