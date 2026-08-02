package org.staacks.alpharemote.feature.wificamera.ui.cameracontrol

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.staacks.alpharemote.core.ui.theme.BluetoothRemoteForSonyCamerasTheme
import org.staacks.alpharemote.core.ui.theme.textConnected

/**
 * Press and hold to focus, release to shoot — the camera's own half-press, because the body
 * refuses to fire until autofocus has been engaged (PROTOCOL.md §2.4).
 *
 * The ring turns amber while focusing and green once the camera reports focus, and the inner disc
 * shrinks and reddens during the exposure, so each stage of the sequence is visible.
 */
@Composable
internal fun ShutterButton(
    enabled: Boolean,
    shutter: ShutterState,
    focusStatus: String?,
    onFocus: () -> Unit,
    onShoot: () -> Unit,
    onCancel: () -> Unit
) {
    val capturing = shutter == ShutterState.CAPTURING
    val focusing = shutter == ShutterState.FOCUSING
    val focusLocked = focusing && focusStatus?.equals("Focused", ignoreCase = true) == true

    val innerSize by animateDpAsState(
        targetValue = when {
            capturing -> 30.dp
            focusing -> 42.dp
            else -> 48.dp
        },
        label = "shutterInner"
    )
    val ringColor = when {
        focusLocked -> MaterialTheme.colorScheme.textConnected
        focusing -> MaterialTheme.colorScheme.secondary
        else -> MaterialTheme.colorScheme.onBackground
    }

    Box(
        modifier = Modifier
            .size(64.dp)
            .clip(CircleShape)
            .border(3.dp, ringColor, CircleShape)
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectTapGestures(
                    onPress = {
                        onFocus()
                        // Released inside the button means take the shot; anywhere else means
                        // the gesture was abandoned and focus should simply be dropped.
                        if (tryAwaitRelease()) onShoot() else onCancel()
                    }
                )
            }
            .alpha(if (enabled) 1f else 0.38f),
        contentAlignment = Alignment.Center
    ) {
        Box(
            Modifier
                .size(innerSize)
                .clip(CircleShape)
                .background(if (capturing) MaterialTheme.colorScheme.error else Color.White)
        )
    }
}

@Preview(name = "Idle", showBackground = true)
@Composable
private fun ShutterButtonIdlePreview() {
    BluetoothRemoteForSonyCamerasTheme {
        ShutterButton(
            enabled = true,
            shutter = ShutterState.IDLE,
            focusStatus = null,
            onFocus = {},
            onShoot = {},
            onCancel = {}
        )
    }
}

@Preview(name = "Focus locked", showBackground = true)
@Composable
private fun ShutterButtonFocusedPreview() {
    BluetoothRemoteForSonyCamerasTheme {
        ShutterButton(
            enabled = true,
            shutter = ShutterState.FOCUSING,
            focusStatus = "Focused",
            onFocus = {},
            onShoot = {},
            onCancel = {}
        )
    }
}

@Preview(name = "Capturing", showBackground = true)
@Composable
private fun ShutterButtonCapturingPreview() {
    BluetoothRemoteForSonyCamerasTheme {
        ShutterButton(
            enabled = true,
            shutter = ShutterState.CAPTURING,
            focusStatus = null,
            onFocus = {},
            onShoot = {},
            onCancel = {}
        )
    }
}

/** The camera has no setter for `actTakePicture` right now — dimmed and untappable. */
@Preview(name = "Disabled", showBackground = true)
@Composable
private fun ShutterButtonDisabledPreview() {
    BluetoothRemoteForSonyCamerasTheme {
        ShutterButton(
            enabled = false,
            shutter = ShutterState.IDLE,
            focusStatus = null,
            onFocus = {},
            onShoot = {},
            onCancel = {}
        )
    }
}
