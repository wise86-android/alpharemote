package org.staacks.alpharemote.ui.help

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.staacks.alpharemote.R

@Composable
fun HelpScreen(
    title: String,
    helpText: String,
    onFaqClick: () -> Unit
) {
    Surface {

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(dimensionResource(id = R.dimen.help_padding))
                .animateContentSize()
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(bottom = dimensionResource(id = R.dimen.headline_margin_bottom))
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.End
            ) {
                Text(
                    text = helpText,
                    modifier = Modifier.fillMaxWidth(),
                    style = MaterialTheme.typography.bodyMedium
                )

                Text(
                    text = stringResource(id = R.string.faq_more),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 20.dp),
                    style = MaterialTheme.typography.bodyMedium
                )

                TextButton(
                    onClick = onFaqClick,
                    modifier = Modifier.padding(top = 8.dp)
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.baseline_help_24),
                        contentDescription = null,
                        modifier = Modifier.size(ButtonDefaults.IconSize)
                    )
                    Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                    Text(text = stringResource(id = R.string.faq))
                }
            }
        }
    }
}
