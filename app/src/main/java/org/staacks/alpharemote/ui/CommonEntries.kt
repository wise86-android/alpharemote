package org.staacks.alpharemote.ui

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.res.stringResource
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.scene.DialogSceneStrategy

fun EntryProviderScope<AlphaRemoteNavKey>.commonEntries(
    navigator: Navigator
) {
    entry<AlphaRemoteNavKey.Help>(metadata = DialogSceneStrategy.dialog()) { key ->
        AlertDialog(
            onDismissRequest = { navigator.goBack() },
            title = { Text(stringResource(key.titleRes)) },
            text = { Text(stringResource(key.textRes)) },
            confirmButton = {
                TextButton(onClick = { navigator.goBack() }) {
                    Text(stringResource(id = android.R.string.ok))
                }
            }
        )
    }
}
