package org.staacks.alpharemote.ui.about

import androidx.compose.ui.platform.LocalContext
import androidx.navigation3.runtime.EntryProviderScope
import org.staacks.alpharemote.ui.AlphaRemoteNavKey
import org.staacks.alpharemote.utils.openUrl

fun EntryProviderScope<AlphaRemoteNavKey>.aboutEntries() {
    entry<AlphaRemoteNavKey.About> {
        val context = LocalContext.current
        AboutScreen(onOpenUrl = { url -> context.openUrl(url) })
    }
}
