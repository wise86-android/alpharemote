package org.staacks.alpharemote.ui.about

import androidx.navigation3.runtime.EntryProviderScope
import org.staacks.alpharemote.ui.AlphaRemoteNavKey

fun EntryProviderScope<AlphaRemoteNavKey>.aboutEntries(
    onOpenUrl: (String) -> Unit
) {
    entry<AlphaRemoteNavKey.About> {
        AboutScreen(onOpenUrl = onOpenUrl)
    }
}
