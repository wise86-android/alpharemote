package org.staacks.alpharemote.feature.wificamera.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.security.NetworkSecurityPolicy
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import org.staacks.alpharemote.feature.wificamera.data.discovery.CameraProtocol
import org.staacks.alpharemote.feature.wificamera.data.discovery.DeviceDescription
import org.staacks.alpharemote.feature.wificamera.data.discovery.DeviceDescriptionParser
import org.staacks.alpharemote.feature.wificamera.data.discovery.SsdpDiscovery
import org.staacks.alpharemote.feature.wificamera.data.liveview.LiveViewStream
import org.staacks.alpharemote.feature.wificamera.data.net.CameraNetwork
import org.staacks.alpharemote.feature.wificamera.data.net.NetworkHttpClient
import org.staacks.alpharemote.feature.wificamera.data.rpc.CameraApiError
import org.staacks.alpharemote.feature.wificamera.data.rpc.CameraApiException
import org.staacks.alpharemote.feature.wificamera.data.rpc.CameraEventLog
import org.staacks.alpharemote.feature.wificamera.data.rpc.CameraEventParser
import org.staacks.alpharemote.feature.wificamera.data.rpc.ScalarWebClient
import org.staacks.alpharemote.feature.wificamera.domain.CameraIdentity
import org.staacks.alpharemote.feature.wificamera.domain.CameraOption
import org.staacks.alpharemote.feature.wificamera.domain.CameraSettingId
import org.staacks.alpharemote.feature.wificamera.domain.CameraSnapshot
import org.staacks.alpharemote.feature.wificamera.domain.FailureReason
import org.staacks.alpharemote.feature.wificamera.domain.LiveViewFrame
import org.staacks.alpharemote.feature.wificamera.domain.WifiCameraConnection
import org.staacks.alpharemote.feature.wificamera.domain.WifiCameraRepository
import org.staacks.alpharemote.feature.wificamera.domain.WifiCredentials
import java.io.IOException
import java.net.URL

/**
 * Owns the camera session: joins the access point, finds the camera, and keeps [camera] current
 * for as long as the connection lasts.
 *
 * A process-wide singleton, matching `AlphaRemoteRepository` in the app module — the camera is a
 * single physical device and two sessions competing for it would fight over the same sockets.
 *
 * The connection is one long-running coroutine rather than a set of callbacks. That way losing
 * the access point cancels discovery, the event poll, and any live view stream together, and
 * there is no state to unwind by hand.
 */
class DefaultWifiCameraRepository private constructor(
    context: Context
) : WifiCameraRepository {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val cameraNetwork = CameraNetwork(context)
    private val connectivityManager =
        ContextCompat.getSystemService(context, ConnectivityManager::class.java)!!

    private val _connection = MutableStateFlow<WifiCameraConnection>(WifiCameraConnection.Idle)
    override val connection: StateFlow<WifiCameraConnection> = _connection.asStateFlow()

    private val _camera = MutableStateFlow(CameraSnapshot())
    override val camera: StateFlow<CameraSnapshot> = _camera.asStateFlow()

    /**
     * The live session's RPC handle. Null unless connected; read from other coroutines, hence
     * volatile.
     */
    @Volatile
    private var session: Session? = null

    private var sessionJob: Job? = null

    private class Session(
        val http: NetworkHttpClient,
        val client: ScalarWebClient,
        val description: DeviceDescription
    )

    override fun connect(credentials: WifiCredentials) {
        if (sessionJob?.isActive == true) return
        sessionJob = scope.launch { runSession(credentials) }
    }

    override fun disconnect() {
        sessionJob?.cancel()
        sessionJob = null
        session = null
        _camera.value = CameraSnapshot()
        _connection.value = WifiCameraConnection.Idle
    }

    private suspend fun runSession(credentials: WifiCredentials) {
        if (!cameraNetwork.hasPermission()) {
            _connection.value = WifiCameraConnection.Failed(FailureReason.MISSING_PERMISSION)
            return
        }

        _connection.value = WifiCameraConnection.JoiningWifi
        try {
            // collectLatest so that losing the network cancels everything running on it.
            cameraNetwork.connect(credentials).collectLatest { network ->
                session = null
                _camera.value = CameraSnapshot()

                if (network == null) {
                    _connection.value =
                        WifiCameraConnection.Failed(FailureReason.WIFI_JOIN_FAILED)
                    return@collectLatest
                }
                runOnNetwork(network)
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            // Backstop for anything the per-network handling did not cover, for the same reason:
            // nothing may escape into the scope's default exception handler.
            Log.e(TAG, "Camera session failed", error)
            _connection.value =
                WifiCameraConnection.Failed(FailureReason.NETWORK_ERROR, error.message)
        } finally {
            session = null
        }
    }

    private suspend fun runOnNetwork(network: Network) {
        val http = NetworkHttpClient(network)

        val candidate = discover(network, http)
        if (candidate !is Candidate.Usable) {
            _connection.value = WifiCameraConnection.Failed(
                reason = (candidate as? Candidate.Unusable)?.reason ?: FailureReason.CAMERA_NOT_FOUND,
                detail = (candidate as? Candidate.Unusable)?.detail
            )
            return
        }

        _connection.value = WifiCameraConnection.Handshaking

        val description = candidate.description
        val actionListUrl = description.actionListUrl ?: run {
            _connection.value = WifiCameraConnection.Failed(FailureReason.WRONG_CAMERA_MODE)
            return
        }
        val client = ScalarWebClient(http, actionListUrl)

        try {
            // Never assume a method exists — build behaviour from what this body reports.
            val apis = client.call(
                service = DeviceDescription.CAMERA_SERVICE,
                method = "getAvailableApiList",
                versions = ScalarWebClient.VERSION_1_0
            ).firstArrayOfStrings()

            _camera.value = CameraSnapshot(availableApis = apis.toSet())
            session = Session(http, client, description)
            _connection.value = WifiCameraConnection.Connected(
                CameraIdentity(
                    friendlyName = description.friendlyName,
                    modelName = description.modelName,
                    udn = description.udn
                )
            )

            pollEvents(client)
        } catch (cancellation: CancellationException) {
            // Disconnecting or losing the access point. Not a failure to report.
            throw cancellation
        } catch (error: Exception) {
            // Deliberately not rethrown: this runs in a launched coroutine, and an exception
            // escaping it would reach the default handler and take the app down with it.
            Log.w(TAG, "Camera session ended", error)
            _connection.value = WifiCameraConnection.Failed(
                FailureReason.NETWORK_ERROR, error.message
            )
        } finally {
            session = null
        }
    }

    /**
     * Runs SSDP until a camera that can actually shoot turns up.
     *
     * Keeps the reason the last unusable device was rejected, so "your camera is in the wrong
     * mode" can be reported instead of the far less helpful "no camera found".
     */
    private suspend fun discover(network: Network, http: NetworkHttpClient): Candidate? {
        _connection.value = WifiCameraConnection.Discovering

        var rejection: Candidate.Unusable? = null
        val usable = withTimeoutOrNull(DISCOVERY_TIMEOUT_MS) {
            SsdpDiscovery(network, connectivityManager).discover()
                .mapNotNull { location -> inspect(http, location) }
                .onEach { if (it is Candidate.Unusable) rejection = it }
                .filterIsInstance<Candidate.Usable>()
                .firstOrNull()
        }
        return usable ?: rejection
    }

    /**
     * Fetches and classifies one discovered device. Null means "not a Sony camera at all".
     *
     * A device that could not be read at all comes back as [Candidate.Unusable] rather than null,
     * so the reason reaches the screen. [discover] only falls back to it when nothing usable was
     * found, so one unreadable device cannot mask a camera found afterwards.
     */
    private suspend fun inspect(http: NetworkHttpClient, location: String): Candidate? = try {
        cleartextRefusal(location)?.let { return it }

        val description = DeviceDescriptionParser.parse(http.getText(location))
        val digitalImaging = description
            .serviceOfType(DeviceDescription.DIGITAL_IMAGING_SERVICE_TYPE)
            ?.scpdUrl
            ?.let { DeviceDescriptionParser.resolve(location, it) }
            ?.let { url ->
                runCatching { DeviceDescriptionParser.parseDigitalImaging(http.getText(url)) }
                    .getOrNull()
            }

        when {
            description.protocol != CameraProtocol.LEGACY && digitalImaging?.isPtp == true ->
                Candidate.Unusable(
                    FailureReason.UNSUPPORTED_PROTOCOL,
                    "${description.modelName} speaks PTP/IP"
                )

            description.protocol != CameraProtocol.LEGACY -> null

            !description.supportsRemoteShooting -> Candidate.Unusable(
                FailureReason.WRONG_CAMERA_MODE,
                // The camera cannot be switched over the API; the user has to do it on the body.
                "${description.modelName} is in \"${digitalImaging?.serverType ?: "transfer"}\" " +
                    "mode. Select \"Ctrl w/ Smartphone\" on the camera."
            )

            else -> Candidate.Usable(description)
        }
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (error: Exception) {
        Log.w(TAG, "Could not read $location", error)
        Candidate.Unusable(
            FailureReason.NETWORK_ERROR,
            error.message ?: error.javaClass.simpleName
        )
    }

    /**
     * The `getEvent` long poll — the reason a change made on the camera body shows up here.
     *
     * The first call asks for the full state; every call after that blocks on the camera until
     * something changes and reports only the difference, which is why the result is merged into
     * the previous snapshot rather than replacing it.
     */
    private suspend fun pollEvents(client: ScalarWebClient) {
        var longPoll = false
        while (currentCoroutineContext().isActive) {
            try {
                val result = client.call(
                    service = DeviceDescription.CAMERA_SERVICE,
                    method = "getEvent",
                    params = ScalarWebClient.paramsOf(JsonPrimitive(longPoll)),
                    readTimeoutMs = NetworkHttpClient.LONG_POLL_READ_TIMEOUT_MS
                )
                logEvent(result, longPoll)
                longPoll = true
            } catch (error: CameraApiException) {
                if (error.isLongPollTimeout) {
                    // Nothing changed within the camera's own poll window. Not a fault.
                    continue
                }
                Log.w(TAG, "getEvent failed", error)
                // Re-read the full state: after an error we cannot trust our accumulated view.
                longPoll = false
                delay(POLL_RETRY_DELAY_MS)
            } catch (error: IOException) {
                Log.w(TAG, "getEvent transport error", error)
                longPoll = false
                delay(POLL_RETRY_DELAY_MS)
            }
        }
    }

    /**
     * Applies an event and reports what it did.
     *
     * Exposure compensation is logged on every refresh rather than only on change, and with its
     * raw entry alongside the parsed value — it is the one setting the camera reports as an index
     * rather than a value, so a wrong reading on screen could come either from the camera or from
     * our reconstruction of it, and the two are indistinguishable without both.
     */
    private fun logEvent(result: JsonArray, longPoll: Boolean) {
        val previous = _camera.value
        val current = CameraEventParser.merge(previous, result)
        _camera.value = current

        if (Log.isLoggable(EVENT_TAG, Log.VERBOSE)) {
            Log.v(EVENT_TAG, "getEvent(longPoll=$longPoll) raw: $result")
        }

        val changes = CameraEventLog.describeChanges(previous, current)
        if (changes.isNotEmpty()) {
            Log.d(EVENT_TAG, "getEvent(longPoll=$longPoll) changed:")
            changes.forEach { Log.d(EVENT_TAG, "  $it") }
        }

        val exposure = CameraSettingId.EXPOSURE_COMPENSATION
        Log.d(
            EVENT_TAG,
            "  exposure: parsed=${CameraEventLog.describe(current[exposure])} " +
                "raw=${CameraEventLog.rawEntry(result, exposure.eventType)}"
        )
    }

    override suspend fun setSetting(id: CameraSettingId, option: CameraOption): Result<Unit> {
        val client = session?.client
            ?: return Result.failure(IllegalStateException("Not connected to a camera"))

        // Logged as sent, so a value that never comes back through getEvent can be told apart
        // from one that was never requested.
        Log.d(EVENT_TAG, "${id.setMethod}(${option.param}) for \"${option.label}\"")

        return runCatching {
            client.call(
                service = DeviceDescription.CAMERA_SERVICE,
                method = id.setMethod,
                params = ScalarWebClient.paramsOf(option.param)
            )
            // Deliberately not written into _camera here: the camera confirms the new value
            // through getEvent, and it is the authority on what actually took effect.
            Unit
        }.onFailure { error ->
            Log.w(EVENT_TAG, "${id.setMethod} rejected: ${error.message}")
        }
    }

    override suspend fun capture(): Result<Unit> {
        val client = session?.client
            ?: return Result.failure(IllegalStateException("Not connected to a camera"))

        return runCatching<Unit> {
            try {
                client.call(
                    service = DeviceDescription.CAMERA_SERVICE,
                    method = "actTakePicture",
                    versions = ScalarWebClient.VERSION_1_0
                )
            } catch (error: CameraApiException) {
                if (error.code != CameraApiError.LONG_SHOOTING) throw error
                // The exposure outlasted the response timeout. The camera is still working and
                // reports the result when it finishes, so wait for it rather than failing.
                client.call(
                    service = DeviceDescription.CAMERA_SERVICE,
                    method = "awaitTakePicture",
                    versions = ScalarWebClient.VERSION_1_0,
                    readTimeoutMs = NetworkHttpClient.LONG_POLL_READ_TIMEOUT_MS
                )
            }
        }
    }

    override fun liveView(): Flow<LiveViewFrame> = flow {
        val current = session ?: throw IllegalStateException("Not connected to a camera")

        val url = startLiveView(current)
        val stream = current.http.openStream(url)

        // A thread blocked in a socket read cannot notice cancellation — it is not at a suspension
        // point. Closing the socket is what unblocks it, so this does that the instant the
        // collector goes away. Without it, leaving the screen would leave the stream running, and
        // the camera streaming, until the read timeout expired.
        val closeOnCancel = currentCoroutineContext()[Job]?.invokeOnCompletion {
            runCatching { stream.close() }
        }

        try {
            val reader = LiveViewStream(stream)
            while (currentCoroutineContext().isActive) {
                val payload = reader.readPayload() ?: break
                // Frame-info payloads carry AF boxes, not pictures. Ignored for now.
                if (payload.isImage) emit(payload.toFrame())
            }
        } finally {
            closeOnCancel?.dispose()
            runCatching { stream.close() }
            withContext(NonCancellable) {
                // Told to stop even when we are being cancelled, or the camera keeps streaming.
                // Bounded, because the usual reason for cancelling is that the network went away,
                // and an unbounded call would then hang on its connect timeout instead.
                withTimeoutOrNull(STOP_LIVE_VIEW_TIMEOUT_MS) {
                    runCatching {
                        current.client.call(
                            service = DeviceDescription.CAMERA_SERVICE,
                            method = "stopLiveview"
                        )
                    }
                }
            }
        }
    }
        .flowOn(Dispatchers.IO)
        // Newest frame only. Queueing would trade latency for frames nobody will ever see.
        .conflate()

    private suspend fun startLiveView(session: Session): String {
        val fromMethod = runCatching {
            session.client.call(
                service = DeviceDescription.CAMERA_SERVICE,
                method = "startLiveview"
            ).firstOrNull()?.jsonPrimitive?.contentOrNull
        }.getOrNull()

        // Bodies without startLiveview still publish the URL in the device description.
        return fromMethod
            ?: session.description.liveViewUrl
            ?: throw IllegalStateException("Camera offers no live view")
    }

    private fun kotlinx.serialization.json.JsonArray.firstArrayOfStrings(): List<String> =
        (firstOrNull() as? kotlinx.serialization.json.JsonArray)
            ?.mapNotNull { it.jsonPrimitive.contentOrNull }
            .orEmpty()

    /**
     * Checks the platform's cleartext policy before making the request, so a blocked address is
     * reported as itself instead of as an opaque transport failure.
     *
     * Asking [NetworkSecurityPolicy] directly rather than matching on an exception message: the
     * policy is the thing that decides, and its answer cannot drift with a platform version.
     */
    private fun cleartextRefusal(location: String): Candidate.Unusable? {
        val host = runCatching { URL(location).host }.getOrNull() ?: return null
        if (NetworkSecurityPolicy.getInstance().isCleartextTrafficPermitted(host)) return null

        return Candidate.Unusable(
            FailureReason.CLEARTEXT_BLOCKED,
            "The camera is at $host, which is not in the app's cleartext allowlist. " +
                "Add it to res/xml/wificamera_network_security_config.xml."
        )
    }

    private sealed interface Candidate {
        data class Usable(val description: DeviceDescription) : Candidate
        data class Unusable(val reason: FailureReason, val detail: String? = null) : Candidate
    }

    companion object {
        private const val TAG = "WifiCameraRepository"

        /**
         * Its own tag so the event traffic can be watched on its own:
         * `adb logcat -s WifiCameraEvent`. Raw event bodies are verbose-only — enable them with
         * `adb shell setprop log.tag.WifiCameraEvent VERBOSE`.
         */
        private const val EVENT_TAG = "WifiCameraEvent"

        private const val DISCOVERY_TIMEOUT_MS = 20_000L
        private const val POLL_RETRY_DELAY_MS = 1_000L
        private const val STOP_LIVE_VIEW_TIMEOUT_MS = 2_000L

        @Volatile
        private var INSTANCE: DefaultWifiCameraRepository? = null

        fun getInstance(context: Context): DefaultWifiCameraRepository =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: DefaultWifiCameraRepository(context.applicationContext)
                    .also { INSTANCE = it }
            }
    }
}
