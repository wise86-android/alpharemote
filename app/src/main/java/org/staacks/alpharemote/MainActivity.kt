package org.staacks.alpharemote

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import org.staacks.alpharemote.ui.MainScreen
import org.staacks.alpharemote.ui.theme.BluetoothRemoteForSonyCamerasTheme

class MainActivity : ComponentActivity() {

    companion object {
        const val TAG: String = "alpharemote"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {
            BluetoothRemoteForSonyCamerasTheme {
                MainScreen()
            }
        }
    }
}
