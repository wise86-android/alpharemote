package org.staacks.alpharemote.feature.ble.ui.settings
import org.staacks.alpharemote.feature.ble.R

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.staacks.alpharemote.feature.ble.camera.CameraAction
import org.staacks.alpharemote.feature.ble.camera.CameraActionPreset
import org.staacks.alpharemote.feature.ble.camera.CameraActionTemplateOption
import org.staacks.alpharemote.core.ui.theme.BluetoothRemoteForSonyCamerasTheme

@Composable
fun CameraActionPickerContent(
    startAction: CameraAction,
    showDelete: Boolean,
    onCancel: () -> Unit,
    onDelete: () -> Unit,
    onSave: (CameraAction) -> Unit,
) {
    var action by remember { mutableStateOf(startAction) }
    var presetExpanded by remember { mutableStateOf(false) }

    val options = action.preset.template.userOptions

    Column(
        modifier = Modifier.padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                painter = painterResource(action.preset.template.icon),
                contentDescription = null,
                tint = Color.Unspecified,
            )
            Text(
                text = action.getName(LocalContext.current),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f),
            )
            if (showDelete) {
                IconButton(onClick = onDelete) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = stringResource(R.string.delete),
                    )
                }
            }
        }

        Text(text = stringResource(R.string.action), style = MaterialTheme.typography.labelSmall)
        Button(onClick = { presetExpanded = true }, modifier = Modifier.fillMaxWidth()) {
            Text(text = stringResource(action.preset.template.name))
        }
        DropdownMenu(expanded = presetExpanded, onDismissRequest = { presetExpanded = false }) {
            CameraActionPreset.entries.forEach { preset ->
                DropdownMenuItem(
                    text = { Text(stringResource(preset.template.name)) },
                    onClick = {
                        val old = action
                        val opt = preset.template.userOptions
                        action = old.copy(
                            preset = preset,
                            toggle = if (opt.contains(CameraActionTemplateOption.TOGGLE)) old.toggle else false,
                            step = if (opt.contains(CameraActionTemplateOption.ADJUST_SPEED)) old.step ?: 0.5f else null,
                        )
                        presetExpanded = false
                    },
                )
            }
        }

        if (options.contains(CameraActionTemplateOption.TOGGLE)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(
                    checked = action.toggle,
                    onCheckedChange = { checked -> action = action.copy(toggle = checked) },
                )
                Text(text = stringResource(R.string.toggle_button))
            }
        }

        if (options.contains(CameraActionTemplateOption.ADJUST_SPEED)) {
            Text(text = stringResource(R.string.speed), style = MaterialTheme.typography.labelSmall)
            Slider(
                value = (action.step ?: 0.5f) * 100f,
                onValueChange = { progress -> action = action.copy(step = progress / 100f) },
                valueRange = 0f..100f,
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            Button(onClick = onCancel) {
                Text(text = stringResource(R.string.cancel))
            }
            Button(
                onClick = { onSave(action) },
                modifier = Modifier.padding(start = 12.dp),
            ) {
                Text(text = stringResource(R.string.save))
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun CameraActionPickerContentPreview() {
    BluetoothRemoteForSonyCamerasTheme {
        Surface {
            CameraActionPickerContent(
                startAction = CameraAction(
                    toggle = true,
                    step = null,
                    preset = CameraActionPreset.SHUTTER,
                ),
                showDelete = true,
                onCancel = {},
                onDelete = {},
                onSave = {},
            )
        }
    }
}
