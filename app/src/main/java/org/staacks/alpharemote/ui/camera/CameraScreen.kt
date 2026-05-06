package org.staacks.alpharemote.ui.camera

import android.content.Context
import android.content.res.ColorStateList
import android.os.SystemClock
import android.text.format.DateUtils
import android.util.TypedValue
import android.widget.ImageView
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.material3.rememberStandardBottomSheetState
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SheetValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.delay
import org.staacks.alpharemote.R
import org.staacks.alpharemote.camera.ButtonCode
import org.staacks.alpharemote.camera.CameraAction
import org.staacks.alpharemote.camera.CameraActionPreset
import org.staacks.alpharemote.camera.CameraState
import org.staacks.alpharemote.camera.JogCode
import org.staacks.alpharemote.service.ServiceState
import org.staacks.alpharemote.ui.theme.ActivityStatusSize
import org.staacks.alpharemote.ui.theme.AdvancedControlsDrawerPeek
import org.staacks.alpharemote.ui.theme.BluetoothRemoteForSonyCamerasTheme
import org.staacks.alpharemote.ui.theme.CustomButtonHeightInActivity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CameraScreen(
    uiState: CameraViewModel.CameraUIState,
    customButtons: List<CameraAction>,
    onGotoSettings: () -> Unit,
    onHelp: () -> Unit,
    onDefaultRemoteTouch: (DefaultRemoteButton.Button, Int) -> Boolean,
    onBulbToggleChanged: (Boolean) -> Unit,
    onBulbDurationChanged: (String) -> Unit,
    onIntervalToggleChanged: (Boolean) -> Unit,
    onIntervalCountChanged: (String) -> Unit,
    onIntervalDurationChanged: (String) -> Unit,
    onStartSequence: () -> Unit,
    onCustomButtonClick: (CameraAction) -> Unit,
) {
    if (!uiState.connected) {
        DisconnectedCameraContent(onGotoSettings = onGotoSettings)
        return
    }

    val sheetState = rememberStandardBottomSheetState(
        initialValue = SheetValue.PartiallyExpanded,
        skipHiddenState = true,
    )

    val sheetContainerColor = colorResource(R.color.gray10)

        BottomSheetScaffold(
            scaffoldState = rememberBottomSheetScaffoldState(bottomSheetState = sheetState),
            sheetPeekHeight = AdvancedControlsDrawerPeek,
            sheetContent = {
                AdvancedControlsSheet(
                    uiState = uiState,
                    customButtons = customButtons,
                    onBulbToggleChanged = onBulbToggleChanged,
                    onBulbDurationChanged = onBulbDurationChanged,
                    onIntervalToggleChanged = onIntervalToggleChanged,
                    onIntervalCountChanged = onIntervalCountChanged,
                    onIntervalDurationChanged = onIntervalDurationChanged,
                    onStartSequence = onStartSequence,
                    onCustomButtonClick = onCustomButtonClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                )
            },
            sheetContainerColor = sheetContainerColor,
            sheetSwipeEnabled = true,
            containerColor = Color.Transparent,
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(horizontal = 4.dp)
            ) {
                StatusHeader(
                    uiState = uiState,
                    onHelp = onHelp,
                )
                DefaultRemote(
                    cameraState = uiState.cameraState,
                    onButtonTouch = onDefaultRemoteTouch,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                )
            }
        }
    }

@Composable
private fun DisconnectedCameraContent(onGotoSettings: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(30.dp)
    ) {
        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(R.string.camera_not_connected),
                modifier = Modifier.width(400.dp),
            )
            Spacer(modifier = Modifier.height(30.dp))
            Button(onClick = onGotoSettings) {
                Icon(painter = painterResource(R.drawable.ic_settings_black_24dp), contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = stringResource(R.string.title_settings))
            }
        }
    }
}

@Composable
private fun StatusHeader(
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
                StatusIcon(icon = R.drawable.status_focus, alpha = if (state?.focus == true) 1f else 0.5f, content = R.string.status_focus)
                StatusIcon(icon = R.drawable.status_shutter, alpha = if (state?.shutter == true) 1f else 0.5f, content = R.string.status_shutter)
                StatusIcon(icon = R.drawable.status_recording, alpha = if (state?.recording == true) 1f else 0.5f, content = R.string.status_recording)
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
            .padding(4.dp)
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

@Composable
private fun DefaultRemote(
    cameraState: CameraState.Ready?,
    onButtonTouch: (DefaultRemoteButton.Button, Int) -> Boolean,
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
    onButtonTouch: (DefaultRemoteButton.Button, Int) -> Boolean,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(modifier = Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LabeledRemoteButton(DefaultRemoteButton.Button.SHUTTER_HALF, R.drawable.ca_shutter_half, R.string.camera_button_half_shutter, cameraState, onButtonTouch, Modifier.weight(1f))
            LabeledRemoteButton(DefaultRemoteButton.Button.SHUTTER, R.drawable.ca_shutter, R.string.camera_button_shutter, cameraState, onButtonTouch, Modifier.weight(1f))
            LabeledRemoteButton(DefaultRemoteButton.Button.SELFTIMER_3S, R.drawable.ca_timer_3s, R.string.camera_button_selftimer_3s, cameraState, onButtonTouch, Modifier.weight(1f))
        }

        Row(modifier = Modifier.fillMaxWidth().weight(1.3f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FocusZoomCard(
                label = R.string.camera_button_focus,
                first = DefaultRemoteButton.Button.FOCUS_FAR,
                firstIcon = R.drawable.ca_focus_far,
                second = DefaultRemoteButton.Button.FOCUS_NEAR,
                secondIcon = R.drawable.ca_focus_near,
                cameraState = cameraState,
                onButtonTouch = onButtonTouch,
                modifier = Modifier.weight(1f),
                vertical = true,
            )

            Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                LabeledRemoteButton(DefaultRemoteButton.Button.RECORD, R.drawable.ca_record, R.string.camera_button_record, cameraState, onButtonTouch, Modifier.weight(1f), tint = false)
                RemoteTouchButton(DefaultRemoteButton.Button.AF_ON, R.drawable.ca_af_on, cameraState, onButtonTouch, Modifier.weight(1f))
                RemoteTouchButton(DefaultRemoteButton.Button.C1, R.drawable.ca_c1, cameraState, onButtonTouch, Modifier.weight(1f))
            }

            FocusZoomCard(
                label = R.string.camera_button_zoom,
                first = DefaultRemoteButton.Button.ZOOM_IN,
                firstIcon = R.drawable.ca_zoom_in,
                second = DefaultRemoteButton.Button.ZOOM_OUT,
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
    onButtonTouch: (DefaultRemoteButton.Button, Int) -> Boolean,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(modifier = Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LabeledRemoteButton(DefaultRemoteButton.Button.SHUTTER_HALF, R.drawable.ca_shutter_half, R.string.camera_button_half_shutter, cameraState, onButtonTouch, Modifier.weight(1f))
            LabeledRemoteButton(DefaultRemoteButton.Button.SHUTTER, R.drawable.ca_shutter, R.string.camera_button_shutter, cameraState, onButtonTouch, Modifier.weight(1f))
            LabeledRemoteButton(DefaultRemoteButton.Button.SELFTIMER_3S, R.drawable.ca_timer_3s, R.string.camera_button_selftimer_3s, cameraState, onButtonTouch, Modifier.weight(1f))
        }

        Row(modifier = Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            FocusZoomCard(
                label = R.string.camera_button_focus,
                first = DefaultRemoteButton.Button.FOCUS_NEAR,
                firstIcon = R.drawable.ca_focus_near,
                second = DefaultRemoteButton.Button.FOCUS_FAR,
                secondIcon = R.drawable.ca_focus_far,
                cameraState = cameraState,
                onButtonTouch = onButtonTouch,
                modifier = Modifier.weight(1.4f),
                vertical = false,
            )
            RemoteTouchButton(DefaultRemoteButton.Button.AF_ON, R.drawable.ca_af_on, cameraState, onButtonTouch, Modifier.weight(0.7f))
            RemoteTouchButton(DefaultRemoteButton.Button.RECORD, R.drawable.ca_record, cameraState, onButtonTouch, Modifier.weight(0.7f), tint = false)
            RemoteTouchButton(DefaultRemoteButton.Button.C1, R.drawable.ca_c1, cameraState, onButtonTouch, Modifier.weight(0.7f))
            FocusZoomCard(
                label = R.string.camera_button_zoom,
                first = DefaultRemoteButton.Button.ZOOM_OUT,
                firstIcon = R.drawable.ca_zoom_out,
                second = DefaultRemoteButton.Button.ZOOM_IN,
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
    button: DefaultRemoteButton.Button,
    @DrawableRes icon: Int,
    @StringRes label: Int,
    cameraState: CameraState.Ready?,
    onButtonTouch: (DefaultRemoteButton.Button, Int) -> Boolean,
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
    button: DefaultRemoteButton.Button,
    @DrawableRes icon: Int,
    cameraState: CameraState.Ready?,
    onButtonTouch: (DefaultRemoteButton.Button, Int) -> Boolean,
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
    first: DefaultRemoteButton.Button,
    @DrawableRes firstIcon: Int,
    second: DefaultRemoteButton.Button,
    @DrawableRes secondIcon: Int,
    cameraState: CameraState.Ready?,
    onButtonTouch: (DefaultRemoteButton.Button, Int) -> Boolean,
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AdvancedControlsSheet(
    uiState: CameraViewModel.CameraUIState,
    customButtons: List<CameraAction>,
    onBulbToggleChanged: (Boolean) -> Unit,
    onBulbDurationChanged: (String) -> Unit,
    onIntervalToggleChanged: (Boolean) -> Unit,
    onIntervalCountChanged: (String) -> Unit,
    onIntervalDurationChanged: (String) -> Unit,
    onStartSequence: () -> Unit,
    onCustomButtonClick: (CameraAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .background(colorResource(R.color.gray10))
            .padding(top = 2.dp, bottom = 10.dp, start = 10.dp, end = 10.dp)
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(vertical = 8.dp)
                .width(40.dp)
                .height(4.dp)
                .background(colorResource(R.color.gray60), MaterialTheme.shapes.small)
        )

        CustomButtonsRow(
            customButtons = customButtons,
            cameraState = uiState.cameraState,
            onCustomButtonClick = onCustomButtonClick,
        )

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(painter = painterResource(R.drawable.adv_bulb), contentDescription = null)
                Spacer(modifier = Modifier.width(16.dp))
                Text(stringResource(R.string.camera_advanced_bulb_title), modifier = Modifier.weight(1f))
                Switch(checked = uiState.bulbToggle, onCheckedChange = onBulbToggleChanged)
            }
            NumberField(
                label = R.string.camera_advanced_bulb_duration,
                suffix = R.string.seconds,
                enabled = uiState.bulbToggle,
                value = uiState.bulbDuration?.toString() ?: "",
                onValueChanged = onBulbDurationChanged,
                keyboardType = KeyboardType.Decimal,
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(painter = painterResource(R.drawable.adv_interval), contentDescription = null)
                Spacer(modifier = Modifier.width(16.dp))
                Text(stringResource(R.string.camera_advanced_interval_title), modifier = Modifier.weight(1f))
                Switch(checked = uiState.intervalToggle, onCheckedChange = onIntervalToggleChanged)
            }
            NumberField(
                label = R.string.camera_advanced_interval_count,
                suffix = null,
                enabled = uiState.intervalToggle,
                value = uiState.intervalCount?.toString() ?: "",
                onValueChanged = onIntervalCountChanged,
                keyboardType = KeyboardType.Number,
            )
            NumberField(
                label = R.string.camera_advanced_interval_duration,
                suffix = R.string.seconds,
                enabled = uiState.intervalToggle,
                value = uiState.intervalDuration?.toString() ?: "",
                onValueChanged = onIntervalDurationChanged,
                keyboardType = KeyboardType.Decimal,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val pendingCount = uiState.serviceState?.pendingTriggerCount ?: 0
                if (pendingCount > 0) {
                    Text(
                        text = stringResource(R.string.camera_advanced_pending_triggers, pendingCount),
                        modifier = Modifier.padding(end = 16.dp),
                    )
                }
                Button(
                    onClick = onStartSequence,
                    enabled = uiState.bulbToggle || uiState.intervalToggle,
                ) {
                    Text(
                        text = if (uiState.serviceState?.countdown == null)
                            stringResource(R.string.camera_advanced_start)
                        else
                            stringResource(R.string.camera_advanced_abort)
                    )
                }
            }
        }
    }
}

@Composable
private fun NumberField(
    @StringRes label: Int,
    @StringRes suffix: Int?,
    enabled: Boolean,
    value: String,
    onValueChanged: (String) -> Unit,
    keyboardType: KeyboardType,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = stringResource(label),
            modifier = Modifier
                .weight(1f)
                .alpha(if (enabled) 1f else 0.5f)
        )
        OutlinedTextField(
            value = value,
            enabled = enabled,
            onValueChange = onValueChanged,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            singleLine = true,
            modifier = Modifier.width(120.dp),
        )
        suffix?.let {
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = stringResource(it), modifier = Modifier.alpha(if (enabled) 1f else 0.5f))
        }
    }
}

@Composable
private fun CustomButtonsRow(
    customButtons: List<CameraAction>,
    cameraState: CameraState.Ready?,
    onCustomButtonClick: (CameraAction) -> Unit,
) {
    val context = LocalContext.current
    val rippleBackground = resolveSelectableItemBackground(context)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(CustomButtonHeightInActivity)
            .padding(10.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        customButtons.forEach { cameraAction ->
            val tint = customActionTint(cameraAction, cameraState)
            AndroidView(
                factory = { viewContext ->
                    ImageView(viewContext).apply {
                        setBackgroundResource(rippleBackground)
                        isClickable = true
                        setOnClickListener { onCustomButtonClick(cameraAction) }
                    }
                },
                update = { imageView ->
                    imageView.setImageDrawable(cameraAction.getIcon(context))
                    imageView.imageTintList = tint?.let { ColorStateList.valueOf(it.toArgb()) }
                },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .aspectRatio(1f),
            )
        }
    }
}

private fun customActionTint(cameraAction: CameraAction, cameraState: CameraState.Ready?): Color? {
    if (cameraAction.preset.template.preserveColor) {
        return null
    }

    return when {
        cameraState == null -> Color(0xFF808080)
        cameraAction.preset.template.referenceButton in cameraState.pressedButtons ||
            cameraAction.preset.template.referenceJog in cameraState.pressedJogs -> Color(0xFFE98A15)
        else -> Color.White
    }
}

private fun buttonIsPressed(button: DefaultRemoteButton.Button, state: CameraState.Ready): Boolean {
    return when (button) {
        DefaultRemoteButton.Button.SHUTTER -> ButtonCode.SHUTTER_FULL in state.pressedButtons
        DefaultRemoteButton.Button.SHUTTER_HALF -> ButtonCode.SHUTTER_HALF in state.pressedButtons
        DefaultRemoteButton.Button.C1 -> ButtonCode.C1 in state.pressedButtons
        DefaultRemoteButton.Button.AF_ON -> ButtonCode.AF_ON in state.pressedButtons
        DefaultRemoteButton.Button.ZOOM_IN -> JogCode.ZOOM_IN in state.pressedJogs
        DefaultRemoteButton.Button.ZOOM_OUT -> JogCode.ZOOM_OUT in state.pressedJogs
        DefaultRemoteButton.Button.FOCUS_FAR -> JogCode.FOCUS_FAR in state.pressedJogs
        DefaultRemoteButton.Button.FOCUS_NEAR -> JogCode.FOCUS_NEAR in state.pressedJogs
        else -> false
    }
}

private fun resolveSelectableItemBackground(context: Context): Int {
    val ripple = TypedValue()
    context.theme.resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, ripple, true)
    return ripple.resourceId
}

@Preview(showBackground = true)
@Composable
private fun CameraScreenPreview() {
    BluetoothRemoteForSonyCamerasTheme {
        Surface {
            CameraScreen(
                uiState = CameraViewModel.CameraUIState(
                    connected = true,
                    serviceState = ServiceState.Running(
                        cameraState = CameraState.Ready(
                            name = "Alpha 7",
                            address = "00:00:00:00:00:00",
                            focus = true,
                            shutter = false,
                            recording = false,
                        ),
                        countdown = null,
                        countdownLabel = null,
                    ),
                    cameraState = CameraState.Ready(
                        name = "Alpha 7",
                        address = "00:00:00:00:00:00",
                        focus = true,
                        shutter = false,
                        recording = false,
                    ),
                ),
                customButtons = listOf(
                    CameraAction(false, null, null, null, CameraActionPreset.SHUTTER),
                    CameraAction(false, null, null, null, CameraActionPreset.AF_ON),
                ),
                onGotoSettings = {},
                onHelp = {},
                onDefaultRemoteTouch = { _, _ -> true },
                onBulbToggleChanged = {},
                onBulbDurationChanged = {},
                onIntervalToggleChanged = {},
                onIntervalCountChanged = {},
                onIntervalDurationChanged = {},
                onStartSequence = {},
                onCustomButtonClick = {},
            )
        }
    }
}


