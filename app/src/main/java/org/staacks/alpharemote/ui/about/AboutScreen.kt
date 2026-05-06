package org.staacks.alpharemote.ui.about

import android.content.res.Configuration
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Description
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.staacks.alpharemote.BuildConfig
import org.staacks.alpharemote.R
import org.staacks.alpharemote.ui.theme.BluetoothRemoteForSonyCamerasTheme
import org.staacks.alpharemote.ui.theme.FragmentMargin

@Composable
fun AboutScreen(onOpenUrl: (String) -> Unit) {
    Surface {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .verticalScroll(rememberScrollState())
                .padding(FragmentMargin)
        ) {
            Text(
                text = stringResource(id = R.string.title_about),
                style = MaterialTheme.typography.displaySmall
            )

            AboutSection(
                title = stringResource(id = R.string.about_author_title),
                text = stringResource(id = R.string.about_author_text)
            ) {
                ActionButtonsRow(
                    buttons = listOf(
                        ActionButtonInfo(
                            text = stringResource(id = R.string.about_blog_label),
                            icon = Icons.Default.Description,
                            url = stringResource(id = R.string.about_blog_url),
                            onClick = onOpenUrl
                        ),
                        ActionButtonInfo(
                            text = stringResource(id = R.string.about_youtube_label),
                            icon = R.drawable.yt_icon_mono_dark,
                            url = stringResource(id = R.string.about_youtube_url),
                            onClick = onOpenUrl
                        )
                    )
                )
            }

            AboutSection(
                title = stringResource(id = R.string.about_support_title),
                text = stringResource(id = R.string.about_support_text)
            ) {
                ActionButtonsRow(
                    buttons = listOf(
                        ActionButtonInfo(
                            text = stringResource(id = R.string.about_coffee_label),
                            icon = R.drawable.buymeacoffee,
                            url = stringResource(id = R.string.about_coffee_url),
                            onClick = onOpenUrl
                        )
                    )
                )
            }

            AboutSection(
                title = stringResource(id = R.string.about_github_title),
                text = stringResource(id = R.string.about_github_text)
            ) {
                ActionButtonsRow(
                    buttons = listOf(
                        ActionButtonInfo(
                            text = stringResource(id = R.string.about_github_code_label),
                            icon = R.drawable.github_mark_white,
                            url = stringResource(id = R.string.about_github_code_url),
                            onClick = onOpenUrl
                        ),
                        ActionButtonInfo(
                            text = stringResource(id = R.string.about_github_issues_label),
                            icon = Icons.Default.BugReport,
                            url = stringResource(id = R.string.about_github_issues_url),
                            onClick = onOpenUrl
                        )
                    )
                )
            }

            Spacer(modifier = Modifier.height(30.dp))
            Text(
                text = stringResource(
                    id = R.string.about_version_info,
                    BuildConfig.VERSION_NAME,
                    BuildConfig.VERSION_CODE
                ),
                style = MaterialTheme.typography.labelSmall,
                textAlign = TextAlign.End,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_NO)
@Composable
private fun AboutScreenPreviewLight() {
    BluetoothRemoteForSonyCamerasTheme {
        AboutScreen(onOpenUrl = {})
    }
}

@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun AboutScreenPreviewDark() {
    BluetoothRemoteForSonyCamerasTheme {
        AboutScreen(onOpenUrl = {})
    }
}
