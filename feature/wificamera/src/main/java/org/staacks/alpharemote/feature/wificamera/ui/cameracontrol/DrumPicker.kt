package org.staacks.alpharemote.feature.wificamera.ui.cameracontrol

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import org.staacks.alpharemote.feature.wificamera.domain.CameraOption
import org.staacks.alpharemote.feature.wificamera.ui.theme.CameraColors
import org.staacks.alpharemote.feature.wificamera.ui.theme.CameraType
import kotlin.math.abs

/**
 * A horizontally snapping strip with a fixed centre mark — turning it feels like spinning a
 * control ring rather than picking from a list.
 *
 * The value is committed when the drum comes to rest, never while it is moving: every commit is
 * a request to the camera, and firing one per scroll frame would flood it.
 */
@Composable
internal fun DrumPicker(
    options: List<CameraOption>,
    selectedIndex: Int,
    onSettled: (Int) -> Unit
) {
    if (options.isEmpty()) {
        Text(
            text = "The camera reported no options for this setting.",
            style = CameraType.hudSmallDim,
            modifier = Modifier.padding(horizontal = 20.dp)
        )
        return
    }

    val itemWidth = 76.dp
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = selectedIndex)
    val scope = rememberCoroutineScope()

    // Whichever item sits closest to the viewport centre, measured rather than derived from
    // scroll offsets so it stays correct with content padding and variable item sizes.
    val centeredIndex by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val centre = (info.viewportStartOffset + info.viewportEndOffset) / 2f
            info.visibleItemsInfo
                .minByOrNull { abs(it.offset + it.size / 2f - centre) }
                ?.index
                ?: selectedIndex
        }
    }

    BoxWithConstraints(Modifier.fillMaxWidth().height(88.dp)) {
        val sidePadding = ((maxWidth - itemWidth) / 2).coerceAtLeast(0.dp)

        // The centre mark, like the index line on an aperture ring.
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .width(itemWidth)
                .fillMaxHeight()
                .clip(RoundedCornerShape(10.dp))
                .background(CameraColors.ChipActive)
        )

        LazyRow(
            state = listState,
            flingBehavior = rememberSnapFlingBehavior(listState),
            contentPadding = PaddingValues(horizontal = sidePadding),
            modifier = Modifier.fillMaxSize()
        ) {
            itemsIndexed(options, key = { _, option -> option.label }) { index, option ->
                val selected = index == centeredIndex
                Box(
                    modifier = Modifier
                        .width(itemWidth)
                        .fillMaxHeight()
                        .clickable { scope.launch { listState.animateScrollToItem(index) } },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = option.label,
                        style = if (selected) {
                            CameraType.hudMedium.copy(fontSize = 19.sp, color = CameraColors.AccentAmber)
                        } else {
                            CameraType.hudSmallDim.copy(color = CameraColors.TextTertiary)
                        }
                    )
                }
            }
        }
    }

    LaunchedEffect(listState, options) {
        snapshotFlow { listState.isScrollInProgress }
            .distinctUntilChanged()
            .filter { scrolling -> !scrolling }
            .collect {
                val index = centeredIndex
                if (index != selectedIndex) onSettled(index)
            }
    }

    // Follow the camera if it changes underneath us, but never yank the drum out of a hand
    // that is currently turning it.
    LaunchedEffect(selectedIndex) {
        if (!listState.isScrollInProgress && centeredIndex != selectedIndex) {
            listState.animateScrollToItem(selectedIndex)
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF18181B)
@Composable
private fun DrumPickerPreview() {
    Box(Modifier.background(CameraColors.SurfaceElevated)) {
        DrumPicker(
            options = listOf("1.4", "1.8", "2.0", "2.8", "4.0", "5.6", "8.0")
                .map { CameraOption.of(it) },
            selectedIndex = 2,
            onSettled = {}
        )
    }
}
