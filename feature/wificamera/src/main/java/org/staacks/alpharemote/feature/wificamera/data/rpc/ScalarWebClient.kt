package org.staacks.alpharemote.feature.wificamera.data.rpc

import android.util.Log
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import org.staacks.alpharemote.feature.wificamera.data.net.NetworkHttpClient
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Sony's Camera Remote API: JSON-RPC over HTTP POST, one endpoint per service.
 *
 * The client's real job is version negotiation. The same method exists at different versions on
 * different bodies and a camera rejects a version it does not implement, so [call] walks a
 * preference list downwards and remembers the version that worked. Without this, code written
 * against one body silently fails on another.
 */
class ScalarWebClient(
    private val http: NetworkHttpClient,
    private val actionListUrl: String
) {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val nextId = AtomicInteger(1)

    /** Method name -> the version that this camera accepted, so we negotiate only once. */
    private val negotiatedVersions = ConcurrentHashMap<String, String>()

    /**
     * Calls [method] and returns its `result` array.
     *
     * @param versions tried in order until one is not rejected as unsupported.
     * @throws CameraApiException when the camera answers with an error.
     */
    suspend fun call(
        service: String,
        method: String,
        params: JsonArray = EMPTY_PARAMS,
        versions: List<String> = DEFAULT_VERSIONS,
        readTimeoutMs: Int = NetworkHttpClient.DEFAULT_READ_TIMEOUT_MS
    ): JsonArray {
        val cacheKey = "$service.$method"
        val candidates = negotiatedVersions[cacheKey]?.let { listOf(it) } ?: versions

        var lastError: CameraApiException? = null
        for (version in candidates) {
            try {
                val result = callAt(service, method, params, version, readTimeoutMs)
                negotiatedVersions[cacheKey] = version
                return result
            } catch (error: CameraApiException) {
                if (!error.isVersionMismatch) throw error
                Log.d(TAG, "$cacheKey rejected version $version (${error.code})")
                lastError = error
            }
        }
        throw lastError ?: CameraApiException(CameraApiError.NO_SUCH_METHOD, "$cacheKey unavailable")
    }

    private suspend fun callAt(
        service: String,
        method: String,
        params: JsonArray,
        version: String,
        readTimeoutMs: Int
    ): JsonArray {
        val id = nextId.getAndIncrement()
        val body = buildJsonObject {
            put("method", method)
            put("params", params)
            put("id", id)
            put("version", version)
        }

        val responseText = http.postJson(
            url = "$actionListUrl/$service",
            body = json.encodeToString(JsonObject.serializer(), body),
            readTimeoutMs = readTimeoutMs
        )

        val response = json.parseToJsonElement(responseText) as? JsonObject
            ?: throw CameraApiException(CameraApiError.ANY, "Malformed response to $method")

        response["error"]?.let { error ->
            val array = error.jsonArray
            val code = array.getOrNull(0)?.jsonPrimitive?.intOrNull ?: CameraApiError.ANY
            val message = array.getOrNull(1)?.jsonPrimitive?.contentOrNullSafe().orEmpty()
            throw CameraApiException(code, "$method: $message")
        }

        return response["result"]?.jsonArray
        // Some methods answer with `results` (plural) instead; treat both as the payload.
            ?: response["results"]?.jsonArray
            ?: EMPTY_PARAMS
    }

    companion object {
        private const val TAG = "ScalarWebClient"

        val EMPTY_PARAMS = JsonArray(emptyList())

        /**
         * Newest first. `getEvent` in particular reports more state at higher versions, and
         * stepping down costs one extra round trip once per method.
         */
        val DEFAULT_VERSIONS = listOf("1.4", "1.3", "1.2", "1.1", "1.0")

        /** Methods that only ever existed at 1.0; skip the negotiation round trips. */
        val VERSION_1_0 = listOf("1.0")

        fun paramsOf(vararg values: JsonElement) = JsonArray(values.toList())
    }
}

/** Error codes from PROTOCOL.md §2.1. */
object CameraApiError {
    const val ANY = 1
    const val TIMEOUT = 2
    const val ILLEGAL_ARGUMENT = 3
    const val ILLEGAL_REQUEST = 5
    const val ILLEGAL_STATE = 7
    const val NO_SUCH_METHOD = 12
    const val UNSUPPORTED_VERSION = 14
    const val UNSUPPORTED_OPERATION = 15
    const val LONG_POLLING_TIMEOUT = 40402
    const val NOT_AVAILABLE_NOW = 40400
    const val CAMERA_NOT_READY = 40401
    const val LONG_SHOOTING = 40403
}

class CameraApiException(val code: Int, message: String) : Exception("[$code] $message") {

    /** The camera understands neither this method nor this version of it — try a lower one. */
    val isVersionMismatch: Boolean
        get() = code == CameraApiError.NO_SUCH_METHOD ||
            code == CameraApiError.UNSUPPORTED_VERSION ||
            code == CameraApiError.UNSUPPORTED_OPERATION

    /**
     * The long poll simply had nothing to report. Expected once every read timeout, not a fault:
     * re-issue immediately without backing off.
     */
    val isLongPollTimeout: Boolean get() = code == CameraApiError.LONG_POLLING_TIMEOUT
}

/** `jsonPrimitive.content` throws on JSON null; this yields null instead. */
private fun kotlinx.serialization.json.JsonPrimitive.contentOrNullSafe(): String? =
    if (this is kotlinx.serialization.json.JsonNull) null else content
