package org.staacks.alpharemote.feature.wificamera.ui.download

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.staacks.alpharemote.core.ui.theme.BluetoothRemoteForSonyCamerasTheme
import org.staacks.alpharemote.core.ui.theme.textConnected

/**
 * Shown when the camera is in "Send to Smartphone".
 *
 * A separate screen rather than a mode of the camera back, because in this mode the camera offers
 * no live view, no settings and no shutter — there is nothing of the camera back left to show.
 */
@Composable
fun DownloadScreen(
    state: DownloadUiState,
    onStart: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = when (state) {
                is DownloadUiState.Finished -> Icons.Filled.CheckCircle
                is DownloadUiState.Failed -> Icons.Filled.ErrorOutline
                else -> Icons.Filled.CloudDownload
            },
            contentDescription = null,
            tint = when (state) {
                is DownloadUiState.Finished -> MaterialTheme.colorScheme.textConnected
                is DownloadUiState.Failed -> MaterialTheme.colorScheme.error
                else -> MaterialTheme.colorScheme.secondary
            },
            modifier = Modifier.size(48.dp)
        )

        Spacer(Modifier.height(16.dp))
        Text(
            state.headline(),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center
        )

        state.detail()?.let { detail ->
            Spacer(Modifier.height(6.dp))
            Text(
                text = detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }

        if (state is DownloadUiState.Running) {
            Spacer(Modifier.height(20.dp))
            // Indeterminate while listing: the total is not known until the camera has been
            // walked, and a bar pinned at zero until then looks like a stall.
            if (state.overallFraction == null) {
                LinearProgressIndicator(
                    color = MaterialTheme.colorScheme.secondary,
                    trackColor = MaterialTheme.colorScheme.outlineVariant,
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                LinearProgressIndicator(
                    progress = { state.overallFraction },
                    color = MaterialTheme.colorScheme.secondary,
                    trackColor = MaterialTheme.colorScheme.outlineVariant,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        Spacer(Modifier.height(28.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (state is DownloadUiState.Running) {
                OutlinedButton(onClick = onCancel) { Text("Cancel") }
            } else {
                Button(
                    onClick = onStart,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondary,
                        contentColor = MaterialTheme.colorScheme.onSecondary
                    )
                ) {
                    Text(if (state is DownloadUiState.Idle) "Download photos" else "Try again")
                }
            }
        }
    }
}

private fun DownloadUiState.headline(): String = when (this) {
    is DownloadUiState.Idle -> "Photos are waiting on the camera"
    is DownloadUiState.Running -> stage
    is DownloadUiState.Finished ->
        if (saved == 0) "Nothing was downloaded" else "Saved $saved photos"

    is DownloadUiState.Failed -> "The download failed"
}

private fun DownloadUiState.detail(): String? = when (this) {
    is DownloadUiState.Idle ->
        "They will be saved to Pictures/AlphaRemote at full quality."

    is DownloadUiState.Running -> fileName
    is DownloadUiState.Finished ->
        if (skipped > 0) "$skipped skipped — the camera offered only previews for those." else null

    is DownloadUiState.Failed -> message
}

@Preview(name = "Idle", showBackground = true, widthDp = 390, heightDp = 500)
@Composable
private fun DownloadScreenIdlePreview() {
    BluetoothRemoteForSonyCamerasTheme {
        DownloadScreen(state = DownloadUiState.Idle, onStart = {}, onCancel = {})
    }
}

@Preview(name = "Listing", showBackground = true, widthDp = 390, heightDp = 500)
@Composable
private fun DownloadScreenListingPreview() {
    BluetoothRemoteForSonyCamerasTheme {
        DownloadScreen(
            state = DownloadUiState.Running(
                stage = "Listing photos (12)…",
                fileName = null,
                overallFraction = null
            ),
            onStart = {},
            onCancel = {}
        )
    }
}

@Preview(name = "Downloading", showBackground = true, widthDp = 390, heightDp = 500)
@Composable
private fun DownloadScreenDownloadingPreview() {
    BluetoothRemoteForSonyCamerasTheme {
        DownloadScreen(
            state = DownloadUiState.Running(
                stage = "Downloading 2 of 5",
                fileName = "DSC00042.JPG",
                overallFraction = 0.4f
            ),
            onStart = {},
            onCancel = {}
        )
    }
}

@Preview(name = "Finished", showBackground = true, widthDp = 390, heightDp = 500)
@Composable
private fun DownloadScreenFinishedPreview() {
    BluetoothRemoteForSonyCamerasTheme {
        DownloadScreen(state = DownloadUiState.Finished(saved = 5, skipped = 0), onStart = {}, onCancel = {})
    }
}

/** Some items offered only a preview, not full quality — reported, not silently dropped. */
@Preview(name = "Finished with skips", showBackground = true, widthDp = 390, heightDp = 500)
@Composable
private fun DownloadScreenFinishedWithSkipsPreview() {
    BluetoothRemoteForSonyCamerasTheme {
        DownloadScreen(state = DownloadUiState.Finished(saved = 3, skipped = 2), onStart = {}, onCancel = {})
    }
}

@Preview(name = "Failed", showBackground = true, widthDp = 390, heightDp = 500)
@Composable
private fun DownloadScreenFailedPreview() {
    BluetoothRemoteForSonyCamerasTheme {
        DownloadScreen(
            state = DownloadUiState.Failed("The camera reported it was busy (503)."),
            onStart = {},
            onCancel = {}
        )
    }
}
