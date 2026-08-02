package org.staacks.alpharemote.feature.wificamera.ui.liveview

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.staacks.alpharemote.core.ui.theme.BluetoothRemoteForSonyCamerasTheme

/** Default [CameraControlScreen.liveView] slot, before a video stream is attached. */
@Composable
internal fun BoxScope.LiveViewPlaceholder() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Filled.PhotoCamera,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            modifier = Modifier.size(48.dp)
        )
    }
}

@Preview(showBackground = true, widthDp = 200, heightDp = 200)
@Composable
private fun LiveViewPlaceholderPreview() {
    BluetoothRemoteForSonyCamerasTheme {
        Box(Modifier.size(200.dp)) {
            LiveViewPlaceholder()
        }
    }
}
