package org.staacks.alpharemote.feature.wificamera.ui

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryStd
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import org.staacks.alpharemote.feature.wificamera.domain.CameraOption
import org.staacks.alpharemote.feature.wificamera.domain.CameraSetting
import org.staacks.alpharemote.feature.wificamera.domain.CameraSettingId
import org.staacks.alpharemote.feature.wificamera.domain.CameraSnapshot
import org.staacks.alpharemote.feature.wificamera.ui.theme.CameraColors
import org.staacks.alpharemote.feature.wificamera.ui.theme.CameraType
import kotlin.math.roundToInt

/**
 * The camera back: live view behind a HUD, values along the bottom.
 *
 * Everything shown comes from the camera's own report — there are no preset value lists, because
 * what is selectable changes with the body and the shooting mode. A setting the camera has not
 * mentioned shows as `--` rather than a guess, and a setting with no setter in the current mode
 * is visible but not touchable.
 *
 * [liveView] is a slot so the video stream can be dropped in later without this file changing.
 */
@Composable
fun CameraControlScreen(
    camera: CameraSnapshot,
    cameraName: String,
    onSelect: (CameraSettingId, CameraOption) -> Unit,
    onFocus: () -> Unit,
    onShoot: () -> Unit,
    onCancelFocus: () -> Unit,
    shutter: ShutterState = ShutterState.IDLE,
    modifier: Modifier = Modifier,
    liveView: @Composable BoxScope.() -> Unit = { LiveViewPlaceholder() }
) {
    var editing by remember { mutableStateOf<CameraSettingId?>(null) }

    Box(modifier.fillMaxSize().background(CameraColors.Background)) {
        Box(Modifier.fillMaxSize()) { liveView() }

        StatusBar(
            camera = camera,
            cameraName = cameraName,
            modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth()
        )

        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth()) {
            ExposureMeterStrip(
                setting = camera[CameraSettingId.EXPOSURE_COMPENSATION],
                onSelect = { option ->
                    onSelect(CameraSettingId.EXPOSURE_COMPENSATION, option)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, CameraColors.SurfaceElevated)
                        )
                    )
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            )
            BottomControlBar(
                camera = camera,
                onFieldTap = { editing = it },
                onFocus = onFocus,
                onShoot = onShoot,
                onCancelFocus = onCancelFocus,
                shutter = shutter
            )
        }
    }

    editing?.let { id ->
        camera[id]?.let { setting ->
            CameraSettingSheet(
                setting = setting,
                onSelect = { option -> onSelect(id, option) },
                onDismiss = { editing = null }
            )
        }
    }
}

@Composable
private fun BoxScope.LiveViewPlaceholder() {
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

@Composable
private fun StatusBar(camera: CameraSnapshot, cameraName: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .background(
                Brush.verticalGradient(listOf(CameraColors.OverlayTop, Color.Transparent))
            )
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Filled.Wifi,
            contentDescription = "Connected",
            tint = CameraColors.AccentTeal,
            modifier = Modifier.size(16.dp)
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = cameraName,
            style = CameraType.label,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false)
        )

        Spacer(Modifier.weight(1f))

        // Both are optional in the protocol: older bodies never report them, so they are simply
        // absent rather than shown as zero.
        camera.storage?.recordableImages?.let {
            Text("$it", style = CameraType.hudSmallDim)
            Spacer(Modifier.width(12.dp))
        }
        camera.battery?.levelPercent?.let {
            Icon(
                imageVector = Icons.Filled.BatteryStd,
                contentDescription = "Battery",
                tint = CameraColors.TextSecondary,
                modifier = Modifier.size(16.dp)
            )
            Spacer(Modifier.width(2.dp))
            Text("$it%", style = CameraType.hudSmallDim)
        }
    }
}

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
private fun ExposureMeterStrip(
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

@Composable
private fun BottomControlBar(
    camera: CameraSnapshot,
    onFieldTap: (CameraSettingId) -> Unit,
    onFocus: () -> Unit,
    onShoot: () -> Unit,
    onCancelFocus: () -> Unit,
    shutter: ShutterState
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                CameraColors.SurfaceElevated,
                RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
            )
            .padding(top = 14.dp, bottom = 12.dp)
    ) {
        // Set occasionally rather than per shot, so these stay quiet outlined chips instead of
        // sharing the amber the exposure values get.
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SecondaryChip(
                icon = Icons.Filled.CenterFocusStrong,
                setting = camera[CameraSettingId.FOCUS_MODE],
                modifier = Modifier.weight(1f),
                onClick = { onFieldTap(CameraSettingId.FOCUS_MODE) }
            )
            // The mockup's second chip is metering, which the legacy API does not expose on any
            // version. White balance is the nearest thing the camera will actually report.
            SecondaryChip(
                icon = Icons.Filled.WbSunny,
                setting = camera[CameraSettingId.WHITE_BALANCE],
                modifier = Modifier.weight(1f),
                onClick = { onFieldTap(CameraSettingId.WHITE_BALANCE) }
            )
        }

        Spacer(Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ExposureChip(
                label = "ISO",
                setting = camera[CameraSettingId.ISO_SPEED_RATE],
                modifier = Modifier.weight(1f),
                onClick = { onFieldTap(CameraSettingId.ISO_SPEED_RATE) }
            )
            ExposureChip(
                label = "APERTURE",
                setting = camera[CameraSettingId.F_NUMBER],
                modifier = Modifier.weight(1f),
                onClick = { onFieldTap(CameraSettingId.F_NUMBER) }
            )
            ExposureChip(
                label = "SHUTTER",
                setting = camera[CameraSettingId.SHUTTER_SPEED],
                modifier = Modifier.weight(1f),
                onClick = { onFieldTap(CameraSettingId.SHUTTER_SPEED) }
            )
        }

        Spacer(Modifier.height(18.dp))

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 28.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            ThumbnailButton()
            ShutterButton(
                enabled = camera.canShoot,
                shutter = shutter,
                focusStatus = camera.focusStatus,
                onFocus = onFocus,
                onShoot = onShoot,
                onCancel = onCancelFocus
            )
            ModeQuickButton(
                setting = camera[CameraSettingId.EXPOSURE_MODE],
                onClick = { onFieldTap(CameraSettingId.EXPOSURE_MODE) }
            )
        }
    }
}

/** Dimmed and inert when the camera offers no setter for this value in its current mode. */
@Composable
private fun SecondaryChip(
    icon: ImageVector,
    setting: CameraSetting?,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val editable = setting?.writable == true && setting.available.isNotEmpty()
    Surface(
        onClick = onClick,
        enabled = editable,
        shape = RoundedCornerShape(8.dp),
        color = Color.Transparent,
        border = BorderStroke(1.dp, CameraColors.Divider),
        modifier = modifier.height(32.dp).alpha(if (editable) 1f else DISABLED_ALPHA)
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(icon, null, tint = CameraColors.TextSecondary, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(6.dp))
            Text(
                text = setting?.current?.label ?: "--",
                style = CameraType.hudSmallDim.copy(fontSize = 12.sp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun ExposureChip(
    label: String,
    setting: CameraSetting?,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val editable = setting?.writable == true && setting.available.isNotEmpty()
    Surface(
        onClick = onClick,
        enabled = editable,
        shape = RoundedCornerShape(12.dp),
        color = CameraColors.ChipIdle,
        modifier = modifier.height(56.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 10.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text(label, style = CameraType.label)
            Spacer(Modifier.height(2.dp))
            Text(
                text = setting
                    ?.let { CameraValueFormat.chipValue(it.id, it.current?.label) }
                    ?: "--",
                style = CameraType.hudMedium,
                color = if (editable) CameraColors.AccentAmber else CameraColors.TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * Press and hold to focus, release to shoot — the camera's own half-press, because the body
 * refuses to fire until autofocus has been engaged (PROTOCOL.md §2.4).
 *
 * The ring turns amber while focusing and green once the camera reports focus, and the inner disc
 * shrinks and reddens during the exposure, so each stage of the sequence is visible.
 */
@Composable
private fun ShutterButton(
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
        focusLocked -> CameraColors.AccentGreen
        focusing -> CameraColors.AccentAmber
        else -> CameraColors.TextPrimary
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
            .alpha(if (enabled) 1f else DISABLED_ALPHA),
        contentAlignment = Alignment.Center
    ) {
        Box(
            Modifier
                .size(innerSize)
                .clip(CircleShape)
                .background(if (capturing) CameraColors.AccentRed else Color.White)
        )
    }
}

/** Placeholder for the last shot. Fetching the postview JPEG comes with the video work. */
@Composable
private fun ThumbnailButton() {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(CameraColors.ChipIdle)
            .border(1.dp, CameraColors.Divider, RoundedCornerShape(8.dp))
    )
}

/** Quiet and icon-free — the mode is set once per outing, not once per shot. */
@Composable
private fun ModeQuickButton(setting: CameraSetting?, onClick: () -> Unit) {
    val editable = setting?.writable == true && setting.available.isNotEmpty()
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(CameraColors.ChipIdle)
            .border(1.dp, CameraColors.Divider, RoundedCornerShape(8.dp))
            .clickable(enabled = editable, onClick = onClick)
            .alpha(if (editable) 1f else DISABLED_ALPHA),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = CameraValueFormat.chipValue(
                CameraSettingId.EXPOSURE_MODE,
                setting?.current?.label
            ),
            style = CameraType.hudMedium,
            color = CameraColors.TextSecondary,
            maxLines = 1
        )
    }
}

private const val DISABLED_ALPHA = 0.4f

/** How long the needle keeps showing a requested value before falling back to the camera's. */
private const val PENDING_TIMEOUT_MS = 3_000L