package org.staacks.alpharemote.ui.wificamera

import androidx.navigation3.runtime.EntryProviderScope
import org.staacks.alpharemote.feature.wificamera.ui.WifiCameraScreen
import org.staacks.alpharemote.feature.wificamera.ui.WifiCameraViewModel
import org.staacks.alpharemote.ui.AlphaRemoteNavKey

fun EntryProviderScope<AlphaRemoteNavKey>.wifiCameraEntries(viewModel: WifiCameraViewModel) {
    entry<AlphaRemoteNavKey.WifiCamera> {
        WifiCameraScreen(viewModel)
    }
}
