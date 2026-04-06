package org.staacks.alpharemote.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.draganddrop.dragAndDropSource
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draganddrop.DragAndDropTransferData
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.staacks.alpharemote.R
import org.staacks.alpharemote.camera.CameraAction
import org.staacks.alpharemote.camera.CameraActionPreset
import org.staacks.alpharemote.ui.theme.BluetoothRemoteForSonyCamerasTheme

@Composable
fun NotificationButtonRow(
    action: CameraAction,
    onEditClick: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    dragHandleTransferData: ((Offset) -> DragAndDropTransferData?)? = null,
) {
    val context = LocalContext.current
    val dragHandleModifier = if (dragHandleTransferData != null) {
        modifier.dragAndDropSource(transferData = dragHandleTransferData)
    } else {
        modifier
    }

    Card(
        modifier = dragHandleModifier
    ) {
            Row(
                modifier = Modifier
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {

            Icon(
                imageVector = Icons.Default.Menu,
                contentDescription = stringResource(R.string.settings_custom_buttons_handle_accessibility),
            )

            Icon(
                painterResource(id = action.preset.template.icon),
                contentDescription = stringResource(R.string.settings_custom_buttons_handle_accessibility),
                modifier = dragHandleModifier,
            )

            Text(
                text = action.getName(context),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Row(
                horizontalArrangement = Arrangement.End,
            ) {
                IconButton(onClick = onEditClick) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Edit",
                    )
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = stringResource(R.string.delete),
                    )
                }
            }

        }
        }
    }


@Preview(showBackground = true)
@Composable
private fun NotificationButtonRowPreview() {
    BluetoothRemoteForSonyCamerasTheme {
        NotificationButtonRow(
            action = CameraAction(
                toggle = false,
                selfTimer = 3.0f,
                duration = null,
                step = null,
                preset = CameraActionPreset.TRIGGER_ONCE,
            ),
            onEditClick = {},
            onDelete = {},
        )
    }
}


