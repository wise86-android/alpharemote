package org.staacks.alpharemote.feature.wificamera.ui.cameracontrol

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.serialization.json.JsonPrimitive
import org.staacks.alpharemote.feature.wificamera.domain.CameraOption
import org.staacks.alpharemote.feature.wificamera.domain.CameraSetting
import org.staacks.alpharemote.feature.wificamera.domain.CameraSettingId
import org.staacks.alpharemote.feature.wificamera.ui.theme.CameraColors
import org.staacks.alpharemote.feature.wificamera.ui.theme.CameraType
import org.staacks.alpharemote.feature.wificamera.ui.theme.DISABLED_ALPHA
import kotlin.math.roundToInt

/**
 * The screen's signature element: an analogue scale rather than a bare number, so the amount of
 * compensation dialled in reads at a glance the way a light meter does.
 *
 * Compensation is a continuous run of evenly spaced steps, so it is set by dragging the needle
 * along the scale rather than through a picker — the gesture matches the quantity. Tapping the
 * scale jumps there.
 *
 * The ticks are the camera's own steps, so a third-stop body and a half-stop body draw different
 * scales.
 */
@Composable
internal fun ExposureMeterStrip(
    setting: CameraSetting?,
    onSelect: (CameraOption) -> Unit,
    modifier: Modifier = Modifier
) {
    val options = setting?.available.orEmpty()
    val editable = setting?.writable == true && options.size > 1
    val cameraIndex = options.indexOfFirst { it.label == setting?.current?.label }
        .takeIf { it >= 0 }

    // Where the finger is, while it is down.
    var dragIndex by remember(options) { mutableStateOf<Int?>(null) }
    // What we asked for and are waiting for the camera to confirm. Without this the needle snaps
    // back to the old value for the round trip, which reads as the drag having failed.
    var pendingIndex by remember(options) { mutableStateOf<Int?>(null) }

    LaunchedEffect(cameraIndex, pendingIndex) {
        if (pendingIndex != null && pendingIndex == cameraIndex) pendingIndex = null
    }
    LaunchedEffect(pendingIndex) {
        // Give up waiting if the camera never reports the value — better a needle that tells the
        // truth late than one that lies indefinitely.
        if (pendingIndex != null) {
            delay(PENDING_TIMEOUT_MS)
            pendingIndex = null
        }
    }

    val shownIndex = dragIndex ?: pendingIndex ?: cameraIndex
    val progress = if (options.size > 1 && shownIndex != null) {
        shownIndex / (options.size - 1f)
    } else {
        0.5f
    }

    fun indexAt(x: Float, width: Float): Int {
        if (options.size < 2 || width <= 0f) return 0
        return ((x / width) * (options.size - 1)).roundToInt().coerceIn(options.indices)
    }

    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Text("EV", style = CameraType.label, modifier = Modifier.padding(end = 10.dp))

        Box(
            Modifier
                .weight(1f)
                // Tall enough to be a real touch target; the scale is drawn in the lower part.
                .height(36.dp)
                .pointerInput(options, editable) {
                    if (!editable) return@pointerInput
                    detectHorizontalDragGestures(
                        onDragStart = { offset ->
                            dragIndex = indexAt(offset.x, size.width.toFloat())
                        },
                        onHorizontalDrag = { change, _ ->
                            change.consume()
                            dragIndex = indexAt(change.position.x, size.width.toFloat())
                        },
                        onDragEnd = {
                            // Committed once, on release: every commit is a request to the
                            // camera, and one per drag frame would flood it.
                            dragIndex?.let { index ->
                                options.getOrNull(index)?.let { option ->
                                    pendingIndex = index
                                    onSelect(option)
                                }
                            }
                            dragIndex = null
                        },
                        onDragCancel = { dragIndex = null }
                    )
                }
                .pointerInput(options, editable) {
                    if (!editable) return@pointerInput
                    detectTapGestures { offset ->
                        val index = indexAt(offset.x, size.width.toFloat())
                        options.getOrNull(index)?.let { option ->
                            pendingIndex = index
                            onSelect(option)
                        }
                    }
                }
                .alpha(if (editable) 1f else DISABLED_ALPHA)
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val scaleTop = size.height * 0.35f
                val scaleHeight = size.height - scaleTop
                val tickCount = if (options.size >= 3) options.size.coerceAtMost(41) else 11
                val spacing = size.width / (tickCount - 1)

                for (tick in 0 until tickCount) {
                    val isCentre = tick == tickCount / 2
                    val tickHeight = if (isCentre) scaleHeight else scaleHeight * 0.45f
                    drawLine(
                        color = if (isCentre) CameraColors.AccentAmberDim else CameraColors.Divider,
                        start = Offset(tick * spacing, size.height - tickHeight),
                        end = Offset(tick * spacing, size.height),
                        strokeWidth = (if (isCentre) 2f else 1f).dp.toPx()
                    )
                }

                val needleX = progress.coerceIn(0f, 1f) * size.width
                drawLine(
                    color = CameraColors.AccentAmber,
                    start = Offset(needleX, scaleTop),
                    end = Offset(needleX, size.height),
                    strokeWidth = 2.dp.toPx()
                )
                // A grip at the top of the needle, so it reads as draggable.
                drawCircle(
                    color = CameraColors.AccentAmber,
                    radius = 5.dp.toPx(),
                    center = Offset(needleX, scaleTop * 0.6f)
                )
            }
        }

        Spacer(Modifier.width(10.dp))
        Text(
            text = shownIndex?.let { options.getOrNull(it)?.label }
                ?: setting?.current?.label
                ?: "--",
            style = CameraType.hudSmallDim.copy(
                color = if (dragIndex != null) CameraColors.AccentAmber
                else CameraColors.TextSecondary
            ),
            modifier = Modifier.widthIn(min = 34.dp)
        )
    }
}

/** How long the needle keeps showing a requested value before falling back to the camera's. */
private const val PENDING_TIMEOUT_MS = 3_000L

@Preview(showBackground = true, backgroundColor = 0xFF18181B)
@Composable
private fun ExposureMeterStripPreview() {
    ExposureMeterStrip(
        setting = CameraSetting(
            id = CameraSettingId.EXPOSURE_COMPENSATION,
            current = CameraOption("-0.7", JsonPrimitive(-2)),
            available = (-9..9).map { index ->
                CameraOption("%+.1f".format(index / 3.0), JsonPrimitive(index))
            },
            writable = true
        ),
        onSelect = {},
        modifier = Modifier.fillMaxWidth().padding(16.dp)
    )
}
