package org.staacks.alpharemote.ui.settings

import android.content.ClipData
import android.content.ClipDescription
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draganddrop.DragAndDropTransferData
import androidx.compose.ui.draganddrop.mimeTypes
import androidx.compose.ui.draganddrop.toAndroidDragEvent
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.staacks.alpharemote.R
import org.staacks.alpharemote.camera.CameraAction
import org.staacks.alpharemote.camera.CameraActionPreset
import org.staacks.alpharemote.ui.theme.BluetoothRemoteForSonyCamerasTheme

@Composable
fun CustomButtonsSettingsSection(
    buttons: List<CameraAction>,
    onAddClick: () -> Unit,
    onHelpClick: () -> Unit,
    onEditClick: (Int, CameraAction) -> Unit,
    onMove: (Int, Int) -> Unit,
    onDelete: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = stringResource(R.string.settings_custom_buttons),
            style = MaterialTheme.typography.headlineSmall,
        )
        Text(
            text = stringResource(R.string.settings_custom_buttons_explanation),
            style = MaterialTheme.typography.bodyMedium,
        )

            buttons.mapIndexed { index, action ->
                NotificationButtonRow(
                    action = action,
                    onEditClick = { onEditClick(index, action) },
                    onDelete = { onDelete(index) },
                    dragHandleTransferData = {
                        DragAndDropTransferData(
                            ClipData.newPlainText("custom_button_index", index.toString()),
                        )
                    },
                    modifier = Modifier
                        .dragAndDropTarget(shouldStartDragAndDrop = { event ->
                            event.mimeTypes().contains(ClipDescription.MIMETYPE_TEXT_PLAIN)
                        }, target = remember {
                            object : DragAndDropTarget {
                                override fun onDrop(event: DragAndDropEvent): Boolean {
                                    val draggedData = event.toAndroidDragEvent().clipData.getItemAt(0).text.toString().toInt()
                                    onMove(draggedData, index)
                                    return true
                                }
                            }
                        })
                )
            }



        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            TextButton(onClick = onAddClick) {
                Text(text = stringResource(R.string.settings_custom_buttons_add))
            }
            TextButton(onClick = onHelpClick) {
                Text(text = stringResource(R.string.help))
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun CustomButtonsSettingsSectionEmptyPreview() {
    BluetoothRemoteForSonyCamerasTheme {
        CustomButtonsSettingsSection(
            buttons = emptyList(),
            onAddClick = {},
            onHelpClick = {},
            onEditClick = { _, _ -> },
            onMove = { _, _ -> },
            onDelete = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun CustomButtonsSettingsSectionThreeButtonsPreview() {
    val sampleButtons = listOf(
        CameraAction(false, null, null, null, CameraActionPreset.TRIGGER_ONCE),
        CameraAction(false, 3.0f, null, null, CameraActionPreset.TRIGGER_ONCE),
        CameraAction(false, null, null, null, CameraActionPreset.RECORD),
    )

    BluetoothRemoteForSonyCamerasTheme {
        CustomButtonsSettingsSection(
            buttons = sampleButtons,
            onAddClick = {},
            onHelpClick = {},
            onEditClick = { _, _ -> },
            onMove = { _, _ -> },
            onDelete = {},
        )
    }
}

