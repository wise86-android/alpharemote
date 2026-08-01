package org.staacks.alpharemote.feature.wificamera.data.net

import android.net.Network
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * The module's only way out to the network.
 *
 * Every connection is opened through [Network.openConnection] rather than [URL.openConnection],
 * which is what pins the request to the camera's access point — see [CameraNetwork].
 *
 * `HttpURLConnection` rather than a client library: the module needs exactly three things —
 * a JSON POST, a byte GET, and a socket held open for tens of minutes while live view streams —
 * and taking a dependency for that would add weight to an app that ships reproducible builds on
 * F-Droid.
 */
class NetworkHttpClient(private val network: Network) {

    /**
     * POSTs [body] and returns the response as text.
     *
     * [readTimeoutMs] is a parameter because the same client serves ordinary calls and
     * `getEvent` long polls, where the camera deliberately holds the connection open until
     * something changes.
     */
    suspend fun postJson(
        url: String,
        body: String,
        readTimeoutMs: Int = DEFAULT_READ_TIMEOUT_MS
    ): String = withContext(Dispatchers.IO) {
        val connection = open(url, readTimeoutMs).apply {
            requestMethod = "POST"
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("Connection", "close")
        }
        try {
            connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            connection.readTextOrThrow()
        } finally {
            connection.disconnect()
        }
    }

    suspend fun getText(url: String): String = withContext(Dispatchers.IO) {
        val connection = open(url, DEFAULT_READ_TIMEOUT_MS).apply {
            requestMethod = "GET"
            setRequestProperty("Connection", "close")
        }
        try {
            connection.readTextOrThrow()
        } finally {
            connection.disconnect()
        }
    }

    /**
     * Opens a stream that stays open — the live view socket.
     *
     * The read timeout is generous rather than absent so a camera that stops sending eventually
     * surfaces as an error instead of hanging the collector forever. The caller owns the returned
     * stream and the connection dies with it.
     */
    suspend fun openStream(
        url: String,
        readTimeoutMs: Int = STREAM_READ_TIMEOUT_MS
    ): InputStream = withContext(Dispatchers.IO) {
        val connection = open(url, readTimeoutMs).apply {
            requestMethod = "GET"
        }
        val status = connection.responseCode
        if (status != HttpURLConnection.HTTP_OK) {
            connection.disconnect()
            throw IOException("HTTP $status opening stream $url")
        }
        ClosingInputStream(connection)
    }

    private fun open(url: String, readTimeoutMs: Int): HttpURLConnection =
        (network.openConnection(URL(url)) as HttpURLConnection).apply {
            connectTimeout = CONNECT_TIMEOUT_MS
            this.readTimeout = readTimeoutMs
            instanceFollowRedirects = false
        }

    private fun HttpURLConnection.readTextOrThrow(): String {
        val status = responseCode
        if (status != HttpURLConnection.HTTP_OK) {
            val detail = errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
            throw IOException("HTTP $status for $url${detail.take(ERROR_BODY_LIMIT)}")
        }
        return inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
    }

    /** Ties the stream's lifetime to its connection so closing one releases the other. */
    private class ClosingInputStream(
        private val connection: HttpURLConnection
    ) : InputStream() {
        private val delegate = connection.inputStream

        override fun read(): Int = delegate.read()
        override fun read(b: ByteArray, off: Int, len: Int): Int = delegate.read(b, off, len)
        override fun available(): Int = delegate.available()

        override fun close() {
            try {
                delegate.close()
            } finally {
                connection.disconnect()
            }
        }
    }

    companion object {
        const val DEFAULT_READ_TIMEOUT_MS = 10_000
        const val LONG_POLL_READ_TIMEOUT_MS = 40_000
        private const val CONNECT_TIMEOUT_MS = 5_000
        private const val STREAM_READ_TIMEOUT_MS = 15_000
        private const val ERROR_BODY_LIMIT = 200
    }
}
