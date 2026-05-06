package org.staacks.alpharemote.ui.camera

import android.content.Context
import android.content.res.ColorStateList
import android.util.TypedValue
import android.widget.ImageView
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import org.staacks.alpharemote.R
import org.staacks.alpharemote.camera.CameraAction
import org.staacks.alpharemote.camera.CameraActionPreset
import org.staacks.alpharemote.camera.CameraState
import org.staacks.alpharemote.service.ServiceState
import org.staacks.alpharemote.ui.theme.BluetoothRemoteForSonyCamerasTheme
import org.staacks.alpharemote.ui.theme.CustomButtonHeightInActivity

@Composable
fun AdvancedControlsSheet(
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

private fun resolveSelectableItemBackground(context: Context): Int {
    val ripple = TypedValue()
    context.theme.resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, ripple, true)
    return ripple.resourceId
}

@Preview(showBackground = true, widthDp = 420)
@Composable
private fun AdvancedControlsSheetPreview() {
    BluetoothRemoteForSonyCamerasTheme {
        Surface {
            AdvancedControlsSheet(
                uiState = CameraViewModel.CameraUIState(
                    connected = true,
                    serviceState = ServiceState.Running(
                        cameraState = CameraState.Ready(
                            name = "Alpha 7",
                            address = "00:00:00:00:00:00",
                            focus = false,
                            shutter = false,
                            recording = false,
                        ),
                        countdown = null,
                        countdownLabel = null,
                        pendingTriggerCount = 3,
                    ),
                    cameraState = CameraState.Ready(
                        name = "Alpha 7",
                        address = "00:00:00:00:00:00",
                        focus = false,
                        shutter = false,
                        recording = false,
                    ),
                    bulbToggle = true,
                    bulbDuration = 5.0,
                    intervalToggle = true,
                    intervalCount = 50,
                    intervalDuration = 3.0,
                ),
                customButtons = listOf(
                    CameraAction(false, null, null, null, CameraActionPreset.SHUTTER),
                    CameraAction(false, null, null, null, CameraActionPreset.AF_ON),
                ),
                onBulbToggleChanged = {},
                onBulbDurationChanged = {},
                onIntervalToggleChanged = {},
                onIntervalCountChanged = {},
                onIntervalDurationChanged = {},
                onStartSequence = {},
                onCustomButtonClick = {},
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

