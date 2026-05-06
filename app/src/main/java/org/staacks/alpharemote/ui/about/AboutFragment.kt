package org.staacks.alpharemote.ui.about

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.net.toUri
import androidx.fragment.app.Fragment
import org.staacks.alpharemote.ui.theme.BluetoothRemoteForSonyCamerasTheme

class AboutFragment : Fragment() {

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                BluetoothRemoteForSonyCamerasTheme {
                    AboutScreen(onOpenUrl = ::openURL)
                }
            }
        }
    }

    private fun openURL(target: String) {
        startActivity(Intent(Intent.ACTION_VIEW, target.toUri()))
    }
}

