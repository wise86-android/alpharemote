package org.staacks.alpharemote.feature.wificamera.data.discovery

import android.net.ConnectivityManager
import android.net.Network
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.MulticastSocket
import java.net.NetworkInterface
import java.net.SocketTimeoutException
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap

/**
 * Finds the camera's UPnP device description URL.
 *
 * Two mechanisms run at once and feed the same flow, because either alone loses cameras:
 * an M-SEARCH that repeats (a camera whose Wi-Fi is still coming up does not answer the first
 * one) and a listener for unsolicited NOTIFY announcements (a camera that has just restarted
 * announces itself and may never answer an M-SEARCH at all). A one-shot search is the second
 * most expensive mistake in PROTOCOL.md's list of pitfalls.
 *
 * Emits each distinct `LOCATION` URL once. Cold, and stops when collection stops.
 */
class SsdpDiscovery(
    private val network: Network,
    private val connectivityManager: ConnectivityManager
) {

    fun discover(): Flow<String> = channelFlow {
        val seen = Collections.newSetFromMap(ConcurrentHashMap<String, Boolean>())

        val emit: suspend (String) -> Unit = { location ->
            if (seen.add(location)) {
                Log.i(TAG, "Discovered $location")
                send(location)
            }
        }

        launch(Dispatchers.IO) { searchLoop(emit) }
        launch(Dispatchers.IO) { listenForAnnouncements(emit) }
    }

    /** Repeats M-SEARCH and collects the unicast replies. */
    private suspend fun searchLoop(emit: suspend (String) -> Unit) {
        DatagramSocket().use { socket ->
            network.bindSocket(socket)
            socket.soTimeout = RECEIVE_TIMEOUT_MS
            socket.broadcast = true

            val payload = M_SEARCH.toByteArray(Charsets.US_ASCII)
            val target = InetSocketAddress(InetAddress.getByName(SSDP_GROUP), SSDP_PORT)
            val buffer = ByteArray(BUFFER_SIZE)

            while (currentCoroutineContext().isActive) {
                runCatching {
                    socket.send(DatagramPacket(payload, payload.size, target))
                }.onFailure { Log.w(TAG, "M-SEARCH send failed", it) }

                // Collect replies for one search interval, then search again.
                val deadline = System.currentTimeMillis() + SEARCH_INTERVAL_MS
                while (currentCoroutineContext().isActive &&
                    System.currentTimeMillis() < deadline
                ) {
                    val packet = DatagramPacket(buffer, buffer.size)
                    try {
                        socket.receive(packet)
                    } catch (_: SocketTimeoutException) {
                        continue
                    }
                    packet.locationHeader()?.let { emit(it) }
                }
            }
        }
    }

    /** Listens on the multicast group for cameras announcing themselves. */
    private suspend fun listenForAnnouncements(emit: suspend (String) -> Unit) {
        val group = InetSocketAddress(InetAddress.getByName(SSDP_GROUP), SSDP_PORT)
        val interfaceName = connectivityManager.getLinkProperties(network)?.interfaceName
        val networkInterface = withContext(Dispatchers.IO) {
            interfaceName?.let { NetworkInterface.getByName(it) }
        }
        if (networkInterface == null) {
            // Without the camera's interface the join would land on the wrong one and receive
            // nothing. The M-SEARCH loop still covers the common case, so this is not fatal.
            Log.w(TAG, "No interface for the camera network, skipping NOTIFY listener")
            return
        }

        MulticastSocket(SSDP_PORT).use { socket ->
            network.bindSocket(socket)
            socket.soTimeout = RECEIVE_TIMEOUT_MS
            socket.joinGroup(group, networkInterface)
            try {
                val buffer = ByteArray(BUFFER_SIZE)
                while (currentCoroutineContext().isActive) {
                    val packet = DatagramPacket(buffer, buffer.size)
                    try {
                        socket.receive(packet)
                    } catch (_: SocketTimeoutException) {
                        continue
                    }
                    val message = packet.text()
                    // byebye means the camera is going away; its LOCATION is not usable.
                    if (message.contains(BYEBYE, ignoreCase = true)) continue
                    message.locationHeader()?.let { emit(it) }
                }
            } finally {
                runCatching { socket.leaveGroup(group, networkInterface) }
            }
        }
    }

    private fun DatagramPacket.text() = String(data, 0, length, Charsets.US_ASCII)

    private fun DatagramPacket.locationHeader() = text().locationHeader()

    companion object {
        private const val TAG = "SsdpDiscovery"
        private const val SSDP_GROUP = "239.255.255.250"
        private const val SSDP_PORT = 1900
        private const val BUFFER_SIZE = 4096
        private const val RECEIVE_TIMEOUT_MS = 1_000
        private const val SEARCH_INTERVAL_MS = 3_000L
        private const val BYEBYE = "ssdp:byebye"

        /**
         * Sent verbatim. `ssdp:all` rather than a Sony-specific target because the official app
         * searches broadly and filters afterwards, and some bodies answer only the broad search.
         */
        private const val M_SEARCH =
            "M-SEARCH * HTTP/1.1\r\n" +
                "HOST: $SSDP_GROUP:$SSDP_PORT\r\n" +
                "MAN: \"ssdp:discover\"\r\n" +
                "MX: 2\r\n" +
                "ST: ssdp:all\r\n" +
                "\r\n"

        /**
         * Pulls `LOCATION` out of an SSDP message. Header names are case-insensitive and bodies
         * disagree about the casing they use, so the match has to be too.
         */
        internal fun String.locationHeader(): String? = lineSequence()
            .map { it.trim() }
            .firstOrNull { it.startsWith("LOCATION:", ignoreCase = true) }
            ?.substringAfter(':')
            ?.trim()
            ?.takeIf { it.startsWith("http://", ignoreCase = true) }
    }
}
