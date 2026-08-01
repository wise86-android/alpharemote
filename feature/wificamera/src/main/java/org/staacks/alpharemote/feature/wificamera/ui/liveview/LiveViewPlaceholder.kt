package org.staacks.alpharemote.feature.wificamera.ui.liveview

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.staacks.alpharemote.feature.wificamera.ui.theme.CameraColors

/** Default [CameraControlScreen.liveView] slot, before a video stream is attached. */
@Composable
internal fun BoxScope.LiveViewPlaceholder() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(CameraColors.LiveViewTop, CameraColors.LiveViewBottom)
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Filled.PhotoCamera,
            contentDescription = null,
            tint = CameraColors.TextTertiary,
            modifier = Modifier.size(48.dp)
        )
    }
}

@Preview(showBackground = true, widthDp = 200, heightDp = 200)
@Composable
private fun LiveViewPlaceholderPreview() {
    Box(Modifier.size(200.dp)) {
        LiveViewPlaceholder()
    }
}
