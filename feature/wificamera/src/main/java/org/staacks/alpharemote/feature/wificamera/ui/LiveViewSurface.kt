package org.staacks.alpharemote.feature.wificamera.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.staacks.alpharemote.feature.wificamera.ui.theme.CameraColors
import org.staacks.alpharemote.feature.wificamera.ui.theme.CameraType

/**
 * The viewfinder.
 *
 * [ContentScale.Fit] rather than Crop: the camera's frame is what will be photographed, so
 * cropping it to fill a phone screen would show a composition that is not the one being taken.
 * The letterboxing is the honest option.
 */
@Composable
fun LiveViewSurface(state: LiveViewState, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(CameraColors.LiveViewTop, CameraColors.LiveViewBottom)
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        when (state) {
            is LiveViewState.Streaming -> Image(
                bitmap = state.frame,
                contentDescription = "Live view",
                contentScale = ContentScale.Fit,
                // Frames are smaller than the screen and get scaled up every time; leaving this
                // at the default costs filtering work per frame for no visible gain at this size.
                filterQuality = FilterQuality.Low,
                modifier = Modifier.fillMaxSize()
            )

            LiveViewState.Starting -> CircularProgressIndicator(
                color = CameraColors.AccentAmber,
                modifier = Modifier.size(28.dp)
            )

            LiveViewState.Idle -> Icon(
                imageVector = Icons.Filled.PhotoCamera,
                contentDescription = null,
                tint = CameraColors.TextTertiary,
                modifier = Modifier.size(48.dp)
            )

            is LiveViewState.Unavailable -> Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.VideocamOff,
                    contentDescription = null,
                    tint = CameraColors.TextTertiary,
                    modifier = Modifier.size(40.dp)
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "No live view",
                    style = CameraType.hudMedium,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = state.message,
                    style = CameraType.hudSmallDim,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
