package org.staacks.alpharemote.ui.camera

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.staacks.alpharemote.R
import org.staacks.alpharemote.camera.ButtonCode
import org.staacks.alpharemote.camera.CameraState
import org.staacks.alpharemote.camera.JogCode
import org.staacks.alpharemote.ui.theme.BluetoothRemoteForSonyCamerasTheme

@Composable
fun DefaultRemote(
    cameraState: CameraState.Ready?,
    onButtonTouch: (RemoteButton, Int) -> Boolean,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier = modifier.padding(top = 8.dp, bottom = 8.dp)) {
        if (maxWidth > maxHeight) {
            DefaultRemoteLandscape(cameraState = cameraState, onButtonTouch = onButtonTouch)
        } else {
            DefaultRemotePortrait(cameraState = cameraState, onButtonTouch = onButtonTouch)
        }
    }
}

@Composable
private fun DefaultRemotePortrait(
    cameraState: CameraState.Ready?,
    onButtonTouch: (RemoteButton, Int) -> Boolean,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            LabeledRemoteButton(RemoteButton.SHUTTER_HALF, R.drawable.ca_shutter_half, R.string.camera_button_half_shutter, cameraState, onButtonTouch, Modifier.weight(1f))
            LabeledRemoteButton(RemoteButton.SHUTTER, R.drawable.ca_shutter, R.string.camera_button_shutter, cameraState, onButtonTouch, Modifier.weight(1f))
            LabeledRemoteButton(RemoteButton.SELFTIMER_3S, R.drawable.ca_timer_3s, R.string.camera_button_selftimer_3s, cameraState, onButtonTouch, Modifier.weight(1f))
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1.3f),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FocusZoomCard(
                label = R.string.camera_button_focus,
                first = RemoteButton.FOCUS_FAR,
                firstIcon = R.drawable.ca_focus_far,
                second = RemoteButton.FOCUS_NEAR,
                secondIcon = R.drawable.ca_focus_near,
                cameraState = cameraState,
                onButtonTouch = onButtonTouch,
                modifier = Modifier.weight(1f),
                vertical = true,
            )

            Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                LabeledRemoteButton(RemoteButton.RECORD, R.drawable.ca_record, R.string.camera_button_record, cameraState, onButtonTouch, Modifier.weight(1f), tint = false)
                RemoteTouchButton(RemoteButton.AF_ON, R.drawable.ca_af_on, cameraState, onButtonTouch, Modifier.weight(1f))
                RemoteTouchButton(RemoteButton.C1, R.drawable.ca_c1, cameraState, onButtonTouch, Modifier.weight(1f))
            }

            FocusZoomCard(
                label = R.string.camera_button_zoom,
                first = RemoteButton.ZOOM_IN,
                firstIcon = R.drawable.ca_zoom_in,
                second = RemoteButton.ZOOM_OUT,
                secondIcon = R.drawable.ca_zoom_out,
                cameraState = cameraState,
                onButtonTouch = onButtonTouch,
                modifier = Modifier.weight(1f),
                vertical = true,
            )
        }
    }
}

@Composable
private fun DefaultRemoteLandscape(
    cameraState: CameraState.Ready?,
    onButtonTouch: (RemoteButton, Int) -> Boolean,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            LabeledRemoteButton(RemoteButton.SHUTTER_HALF, R.drawable.ca_shutter_half, R.string.camera_button_half_shutter, cameraState, onButtonTouch, Modifier.weight(1f))
            LabeledRemoteButton(RemoteButton.SHUTTER, R.drawable.ca_shutter, R.string.camera_button_shutter, cameraState, onButtonTouch, Modifier.weight(1f))
            LabeledRemoteButton(RemoteButton.SELFTIMER_3S, R.drawable.ca_timer_3s, R.string.camera_button_selftimer_3s, cameraState, onButtonTouch, Modifier.weight(1f))
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FocusZoomCard(
                label = R.string.camera_button_focus,
                first = RemoteButton.FOCUS_NEAR,
                firstIcon = R.drawable.ca_focus_near,
                second = RemoteButton.FOCUS_FAR,
                secondIcon = R.drawable.ca_focus_far,
                cameraState = cameraState,
                onButtonTouch = onButtonTouch,
                modifier = Modifier.weight(1.4f),
                vertical = false,
            )
            RemoteTouchButton(RemoteButton.AF_ON, R.drawable.ca_af_on, cameraState, onButtonTouch, Modifier.weight(0.7f))
            RemoteTouchButton(RemoteButton.RECORD, R.drawable.ca_record, cameraState, onButtonTouch, Modifier.weight(0.7f), tint = false)
            RemoteTouchButton(RemoteButton.C1, R.drawable.ca_c1, cameraState, onButtonTouch, Modifier.weight(0.7f))
            FocusZoomCard(
                label = R.string.camera_button_zoom,
                first = RemoteButton.ZOOM_OUT,
                firstIcon = R.drawable.ca_zoom_out,
                second = RemoteButton.ZOOM_IN,
                secondIcon = R.drawable.ca_zoom_in,
                cameraState = cameraState,
                onButtonTouch = onButtonTouch,
                modifier = Modifier.weight(1.4f),
                vertical = false,
            )
        }
    }
}

@Composable
private fun LabeledRemoteButton(
    button: RemoteButton,
    @DrawableRes icon: Int,
    @StringRes label: Int,
    cameraState: CameraState.Ready?,
    onButtonTouch: (RemoteButton, Int) -> Boolean,
    modifier: Modifier = Modifier,
    tint: Boolean = true,
) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        RemoteTouchButton(button, icon, cameraState, onButtonTouch, Modifier.weight(1f), tint)
        Text(
            text = stringResource(label),
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun RemoteTouchButton(
    button: RemoteButton,
    @DrawableRes icon: Int,
    cameraState: CameraState.Ready?,
    onButtonTouch: (RemoteButton, Int) -> Boolean,
    modifier: Modifier = Modifier,
    tint: Boolean = true,
) {
    val iconTint = when {
        !tint -> Color.Unspecified
        cameraState == null -> colorResource(R.color.gray50)
        buttonIsPressed(button, cameraState) -> MaterialTheme.colorScheme.secondary
        else -> colorResource(R.color.white)
    }

    Icon(
        painter = painterResource(icon),
        contentDescription = button.name,
        tint = iconTint,
        modifier = modifier
            .fillMaxWidth()
            .pointerInteropFilter { event -> onButtonTouch(button, event.action) }
            .padding(6.dp)
            .aspectRatio(1f),
    )
}

@Composable
private fun FocusZoomCard(
    @StringRes label: Int,
    first: RemoteButton,
    @DrawableRes firstIcon: Int,
    second: RemoteButton,
    @DrawableRes secondIcon: Int,
    cameraState: CameraState.Ready?,
    onButtonTouch: (RemoteButton, Int) -> Boolean,
    modifier: Modifier = Modifier,
    vertical: Boolean,
) {
    Card(
        modifier = modifier.fillMaxHeight(),
        colors = CardDefaults.cardColors(containerColor = colorResource(R.color.gray20)),
    ) {
        if (vertical) {
            Column(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                RemoteTouchButton(first, firstIcon, cameraState, onButtonTouch, Modifier.weight(1f))
                Text(text = stringResource(label), textAlign = TextAlign.Center)
                RemoteTouchButton(second, secondIcon, cameraState, onButtonTouch, Modifier.weight(1f))
            }
        } else {
            Row(modifier = Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                RemoteTouchButton(first, firstIcon, cameraState, onButtonTouch, Modifier.weight(1f))
                Text(text = stringResource(label), modifier = Modifier.wrapContentWidth())
                RemoteTouchButton(second, secondIcon, cameraState, onButtonTouch, Modifier.weight(1f))
            }
        }
    }
}

private fun buttonIsPressed(button: RemoteButton, state: CameraState.Ready): Boolean {
    return when (button) {
        RemoteButton.SHUTTER -> ButtonCode.SHUTTER_FULL in state.pressedButtons
        RemoteButton.SHUTTER_HALF -> ButtonCode.SHUTTER_HALF in state.pressedButtons
        RemoteButton.C1 -> ButtonCode.C1 in state.pressedButtons
        RemoteButton.AF_ON -> ButtonCode.AF_ON in state.pressedButtons
        RemoteButton.ZOOM_IN -> JogCode.ZOOM_IN in state.pressedJogs
        RemoteButton.ZOOM_OUT -> JogCode.ZOOM_OUT in state.pressedJogs
        RemoteButton.FOCUS_FAR -> JogCode.FOCUS_FAR in state.pressedJogs
        RemoteButton.FOCUS_NEAR -> JogCode.FOCUS_NEAR in state.pressedJogs
        else -> false
    }
}

@Preview(showBackground = true, widthDp = 380, heightDp = 720)
@Composable
private fun DefaultRemotePreview() {
    BluetoothRemoteForSonyCamerasTheme {
        Surface {
            DefaultRemote(
                cameraState = CameraState.Ready(
                    name = "Alpha 7",
                    address = "00:00:00:00:00:00",
                    focus = true,
                    shutter = false,
                    recording = false,
                ),
                onButtonTouch = { _, _ -> true },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

