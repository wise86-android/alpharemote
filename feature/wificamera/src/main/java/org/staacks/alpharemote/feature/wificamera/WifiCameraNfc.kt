package org.staacks.alpharemote.feature.wificamera

import android.app.Activity
import android.app.PendingIntent
import android.content.Intent
import android.content.IntentFilter
import android.nfc.NfcAdapter
import android.util.Log
import org.staacks.alpharemote.feature.wificamera.data.nfc.CameraNfcReader
import org.staacks.alpharemote.feature.wificamera.data.nfc.SonyNfcTagParser
import org.staacks.alpharemote.feature.wificamera.domain.WifiCredentials

/**
 * The module's NFC entry point, for the host app to call from its launcher activity.
 *
 * Touching the camera is the whole setup flow: the tag carries the SSID and password, and the
 * camera's own NFC controller starts its Wi-Fi in response to the tap. Nothing has to be sent to
 * the camera — the app reads the tag, then waits for that access point to appear
 * (PROTOCOL.md §1.2).
 */
object WifiCameraNfc {

    private const val TAG = "WifiCameraNfc"

    /** True when this intent is a Sony camera tap, so the caller can route it here. */
    fun isCameraTap(intent: Intent?): Boolean = CameraNfcReader.isCameraTap(intent)

    /**
     * Reads a tapped camera's credentials.
     *
     * @return the credentials, or null if the tag was not readable — the caller can then leave the
     *   UI as it was rather than navigating somewhere that will only show an error.
     */
    suspend fun handleTap(intent: Intent): WifiCredentials? {
        if (!CameraNfcReader.isCameraTap(intent)) return null

        val tag = CameraNfcReader.read(intent)
        if (tag == null || !tag.hasCredentials) {
            Log.w(TAG, "Tapped a Sony tag but it carried no credentials")
            return null
        }

        val credentials = WifiCredentials(ssid = tag.ssid!!, password = tag.password!!)
        Log.i(TAG, "Tapped ${credentials.ssid}")

        return credentials
    }

    /**
     * Claims NFC taps while [activity] is in the foreground.
     *
     * Without this the tap is dispatched through the normal intent filters, which can hand a
     * camera tap to another app — or bounce this one through a fresh launch while it is already
     * open and in the middle of a session. Call from `onResume`.
     */
    fun enableForegroundDispatch(activity: Activity) {
        val adapter = NfcAdapter.getDefaultAdapter(activity) ?: return

        val intent = Intent(activity, activity.javaClass)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val pendingIntent = PendingIntent.getActivity(
            activity,
            0,
            intent,
            PendingIntent.FLAG_MUTABLE
        )

        val filter = IntentFilter(NfcAdapter.ACTION_NDEF_DISCOVERED).apply {
            runCatching { addDataType(SonyNfcTagParser.MIME_TYPE) }
                .onFailure { Log.w(TAG, "Could not filter on the Sony MIME type", it) }
        }

        runCatching {
            adapter.enableForegroundDispatch(activity, pendingIntent, arrayOf(filter), null)
        }.onFailure { Log.w(TAG, "Could not enable NFC foreground dispatch", it) }
    }

    /** Call from `onPause`; the system requires it to be paired with the enable. */
    fun disableForegroundDispatch(activity: Activity) {
        val adapter = NfcAdapter.getDefaultAdapter(activity) ?: return
        runCatching { adapter.disableForegroundDispatch(activity) }
    }
}
