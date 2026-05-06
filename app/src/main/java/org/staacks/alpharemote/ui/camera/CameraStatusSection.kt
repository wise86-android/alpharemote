package org.staacks.alpharemote.ui.camera

import android.os.SystemClock
import android.text.format.DateUtils
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import kotlinx.coroutines.delay
import org.staacks.alpharemote.R
import org.staacks.alpharemote.camera.CameraState
import org.staacks.alpharemote.service.ServiceState
import org.staacks.alpharemote.ui.theme.ActivityStatusSize
import org.staacks.alpharemote.ui.theme.BluetoothRemoteForSonyCamerasTheme

@Composable
fun StatusHeader(
    uiState: CameraViewModel.CameraUIState,
    onHelp: () -> Unit,
) {
    val state = uiState.cameraState
    val serviceState = uiState.serviceState

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
    ) {
        Column {
            Text(
                text = state?.name ?: stringResource(R.string.settings_camera_unknown_name),
                style = MaterialTheme.typography.titleLarge,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusIcon(
                    icon = R.drawable.status_focus,
                    alpha = if (state?.focus == true) 1f else 0.5f,
                    content = R.string.status_focus,
                )
                StatusIcon(
                    icon = R.drawable.status_shutter,
                    alpha = if (state?.shutter == true) 1f else 0.5f,
                    content = R.string.status_shutter,
                )
                StatusIcon(
                    icon = R.drawable.status_recording,
                    alpha = if (state?.recording == true) 1f else 0.5f,
                    content = R.string.status_recording,
                )
            }
        }

        if (serviceState?.countdownLabel == null) {
            IconButton(onClick = onHelp) {
                Icon(
                    painter = painterResource(R.drawable.baseline_help_24),
                    contentDescription = stringResource(R.string.help),
                )
            }
        } else {
            Column(horizontalAlignment = Alignment.End) {
                Text(text = serviceState.countdownLabel)
                serviceState.countdown?.let { CountdownLabel(countdownBase = it) }
            }
        }
    }
}

@Composable
private fun StatusIcon(@DrawableRes icon: Int, alpha: Float, @StringRes content: Int) {
    Icon(
        painter = painterResource(icon),
        contentDescription = stringResource(content),
        tint = colorResource(R.color.white),
        modifier = Modifier
            .padding(androidx.compose.ui.unit.Dp(4f))
            .alpha(alpha)
            .height(ActivityStatusSize)
            .aspectRatio(1f),
    )
}

@Composable
private fun CountdownLabel(countdownBase: Long) {
    val now by produceState(initialValue = SystemClock.elapsedRealtime(), key1 = countdownBase) {
        while (true) {
            value = SystemClock.elapsedRealtime()
            delay(200)
        }
    }
    val seconds = ((countdownBase - now).coerceAtLeast(0L)) / 1000L
    Text(text = DateUtils.formatElapsedTime(seconds), style = MaterialTheme.typography.titleLarge)
}

@Preview(showBackground = true)
@Composable
private fun StatusHeaderPreview() {
    BluetoothRemoteForSonyCamerasTheme {
        Surface {
            StatusHeader(
                uiState = CameraViewModel.CameraUIState(
                    connected = true,
                    serviceState = ServiceState.Running(
                        cameraState = CameraState.Ready(
                            name = "Alpha 7",
                            address = "00:00:00:00:00:00",
                            focus = true,
                            shutter = false,
                            recording = true,
                        ),
                        countdown = null,
                        countdownLabel = null,
                    ),
                    cameraState = CameraState.Ready(
                        name = "Alpha 7",
                        address = "00:00:00:00:00:00",
                        focus = true,
                        shutter = false,
                        recording = true,
                    ),
                ),
                onHelp = {},
            )
        }
    }
}

