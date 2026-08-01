package org.staacks.alpharemote.feature.wificamera.ui.cameracontrol

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.staacks.alpharemote.feature.wificamera.ui.theme.CameraColors

/** Placeholder for the last shot. Fetching the postview JPEG comes with the video work. */
@Composable
internal fun ThumbnailButton() {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(CameraColors.ChipIdle)
            .border(1.dp, CameraColors.Divider, RoundedCornerShape(8.dp))
    )
}

@Preview(showBackground = true, backgroundColor = 0xFF0D0D0F)
@Composable
private fun ThumbnailButtonPreview() {
    ThumbnailButton()
}
