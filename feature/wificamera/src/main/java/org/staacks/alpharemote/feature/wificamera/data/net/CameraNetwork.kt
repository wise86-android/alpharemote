package org.staacks.alpharemote.feature.wificamera.data.net

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiManager
import android.net.wifi.WifiNetworkSpecifier
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import org.staacks.alpharemote.feature.wificamera.domain.WifiCredentials

/**
 * Joins the camera's access point and hands out the [Network] it lives on.
 *
 * The binding is the point of this class, not a detail. The camera AP has no uplink, so Android
 * keeps routing ordinary traffic over cellular; requests aimed at the camera then vanish into the
 * internet and time out in a way that looks exactly like a broken camera. Every socket this
 * module opens is bound to the network produced here (PROTOCOL.md §1.3).
 *
 * A [WifiNetworkSpecifier] request is also the only way to join a specific SSID on a modern
 * Android without sending the user to Settings. It shows a system dialog, and the resulting
 * network is scoped to this app and torn down when the request is released — which is why the
 * flow must stay collected for as long as the camera session lasts.
 */
class CameraNetwork(private val context: Context) {

    private val connectivityManager =
        ContextCompat.getSystemService(context, ConnectivityManager::class.java)!!

    private val wifiManager =
        ContextCompat.getSystemService(context, WifiManager::class.java)!!

    fun hasPermission(): Boolean = ContextCompat.checkSelfPermission(
        context, Manifest.permission.NEARBY_WIFI_DEVICES
    ) == PackageManager.PERMISSION_GRANTED

    /**
     * Emits the camera's network once joined, and null when it is lost.
     *
     * Cancelling collection releases the network request, which disconnects from the camera's
     * access point and lets the phone fall back to its normal Wi-Fi.
     */
    @SuppressLint("MissingPermission")
    fun connect(credentials: WifiCredentials): Flow<Network?> = callbackFlow {
        require(hasPermission()) { "NEARBY_WIFI_DEVICES permission is required to join the camera" }

        // Without a multicast lock the Wi-Fi hardware drops multicast packets before they reach
        // us, and SSDP NOTIFY announcements never arrive.
        val multicastLock = wifiManager.createMulticastLock(MULTICAST_LOCK_TAG).apply {
            setReferenceCounted(false)
            acquire()
        }

        val specifier = WifiNetworkSpecifier.Builder()
            .setSsid(credentials.ssid)
            .setWpa2Passphrase(credentials.password)
            .build()

        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            // The camera AP has no internet. Leaving this capability in place makes the request
            // unsatisfiable and it would simply never fire.
            .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .setNetworkSpecifier(specifier)
            .build()

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                Log.i(TAG, "Joined ${credentials.ssid}")
                trySend(network)
            }

            override fun onLost(network: Network) {
                Log.i(TAG, "Lost ${credentials.ssid}")
                trySend(null)
            }

            override fun onUnavailable() {
                Log.w(TAG, "Could not join ${credentials.ssid}")
                trySend(null)
            }
        }

        connectivityManager.requestNetwork(request, callback)

        awaitClose {
            multicastLock.release()
            connectivityManager.unregisterNetworkCallback(callback)
        }
    }

    companion object {
        private const val TAG = "CameraNetwork"
        private const val MULTICAST_LOCK_TAG = "alpharemote-ssdp"
    }
}
