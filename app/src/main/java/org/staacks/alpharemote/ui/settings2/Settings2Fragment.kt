package org.staacks.alpharemote.ui.settings2

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup

import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import org.staacks.alpharemote.ui.theme.BluetoothRemoteForSonyCamerasTheme
import kotlin.getValue

class Settings2Fragment : Fragment() {
    // Initialize the ViewModel using the custom factory
    private val viewModel: Settings2ViewModel by viewModels { Settings2ViewModelFactory(requireContext()) }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setContent {
                BluetoothRemoteForSonyCamerasTheme {
                    SettingsScreen(viewModel = viewModel)
                }
            }
        }
    }

}