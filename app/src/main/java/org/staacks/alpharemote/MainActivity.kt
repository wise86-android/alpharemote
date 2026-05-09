package org.staacks.alpharemote

import android.bluetooth.le.ScanResult
import android.companion.CompanionDeviceManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.net.toUri
import org.staacks.alpharemote.ui.MainScreen
import org.staacks.alpharemote.ui.settings.CompanionDeviceHelper
import org.staacks.alpharemote.ui.theme.BluetoothRemoteForSonyCamerasTheme
import java.io.Serializable

class MainActivity : ComponentActivity() {

    companion object {
        const val TAG: String = "alpharemote"
    }

    private val onDeviceFoundLauncher = registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { activityResult ->
        if (activityResult.resultCode == RESULT_OK) {
            val scanResult: ScanResult? = extractScanResult(activityResult.data)
            scanResult?.let { result ->
                CompanionDeviceHelper.startObservingDevicePresence(this, result.device)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {
            BluetoothRemoteForSonyCamerasTheme {
                MainScreen(
                    onPairRequested = { chooserLauncher ->
                        onDeviceFoundLauncher.launch(
                            IntentSenderRequest.Builder(chooserLauncher).build()
                        )
                    },
                    onOpenUrl = { url ->
                        startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
                    }
                )
            }
        }
    }

    private fun extractScanResult(intent: Intent?): ScanResult? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent?.getParcelableExtra(CompanionDeviceManager.EXTRA_DEVICE, ScanResult::class.java)
        } else {
            intent?.getParcelableExtra(CompanionDeviceManager.EXTRA_DEVICE)
        }
    }
}
