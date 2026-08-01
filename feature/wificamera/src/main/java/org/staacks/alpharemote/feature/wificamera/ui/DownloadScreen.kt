package org.staacks.alpharemote.feature.wificamera.ui

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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.staacks.alpharemote.feature.wificamera.ui.theme.CameraColors
import org.staacks.alpharemote.feature.wificamera.ui.theme.CameraType

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
            .background(CameraColors.Background)
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
                is DownloadUiState.Finished -> CameraColors.AccentGreen
                is DownloadUiState.Failed -> CameraColors.AccentRed
                else -> CameraColors.AccentAmber
            },
            modifier = Modifier.size(48.dp)
        )

        Spacer(Modifier.height(16.dp))
        Text(state.headline(), style = CameraType.hudMedium, textAlign = TextAlign.Center)

        state.detail()?.let { detail ->
            Spacer(Modifier.height(6.dp))
            Text(
                text = detail,
                style = CameraType.hudSmallDim,
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
                    color = CameraColors.AccentAmber,
                    trackColor = CameraColors.Divider,
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                LinearProgressIndicator(
                    progress = { state.overallFraction },
                    color = CameraColors.AccentAmber,
                    trackColor = CameraColors.Divider,
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
                        containerColor = CameraColors.AccentAmber,
                        contentColor = CameraColors.Background
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
