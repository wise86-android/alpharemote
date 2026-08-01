package org.staacks.alpharemote

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import org.staacks.alpharemote.feature.wificamera.WifiCameraNfc
import org.staacks.alpharemote.feature.wificamera.domain.WifiCredentials
import org.staacks.alpharemote.ui.MainScreen
import org.staacks.alpharemote.ui.theme.BluetoothRemoteForSonyCamerasTheme

class MainActivity : ComponentActivity() {

    companion object {
        const val TAG: String = "alpharemote"
    }

    /**
     * A camera touched to the phone, waiting to be acted on.
     *
     * Held here rather than passed straight to a view model because the tap can arrive before
     * there is any composition to receive it — launching the app *is* the tap, in the common case.
     */
    private var tappedCamera by mutableStateOf<WifiCredentials?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        // The intent that launched the app may itself be the tap.
        handleCameraTap(intent)

        setContent {
            BluetoothRemoteForSonyCamerasTheme {
                MainScreen(
                    tappedCamera = tappedCamera,
                    onTappedCameraHandled = { tappedCamera = null }
                )
            }
        }
    }

    /** Taps that arrive while the app is already open; `singleTop` routes them here. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleCameraTap(intent)
    }

    override fun onResume() {
        super.onResume()
        // Claims taps while we are in front, so a camera touched during a session reaches this
        // activity rather than being dispatched elsewhere.
        WifiCameraNfc.enableForegroundDispatch(this)
    }

    override fun onPause() {
        WifiCameraNfc.disableForegroundDispatch(this)
        super.onPause()
    }

    private fun handleCameraTap(intent: Intent?) {
        if (intent == null || !WifiCameraNfc.isCameraTap(intent)) return
        lifecycleScope.launch {
            // Null when the tag turned out to carry no credentials; the UI then stays where it is
            // rather than jumping to a screen that can only report a failure.
            WifiCameraNfc.handleTap(this@MainActivity, intent)?.let { tappedCamera = it }
        }
    }
}
