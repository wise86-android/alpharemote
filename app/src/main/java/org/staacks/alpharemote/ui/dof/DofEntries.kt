package org.staacks.alpharemote.ui.dof

import androidx.navigation3.runtime.EntryProviderScope
import org.staacks.alpharemote.feature.dof.DofScreen
import org.staacks.alpharemote.feature.dof.DofViewModel
import org.staacks.alpharemote.ui.AlphaRemoteNavKey

fun EntryProviderScope<AlphaRemoteNavKey>.depthOfFieldEntries(viewModel: DofViewModel) {
    entry<AlphaRemoteNavKey.DepthOfField> {
        DofScreen(viewModel)
    }
}
