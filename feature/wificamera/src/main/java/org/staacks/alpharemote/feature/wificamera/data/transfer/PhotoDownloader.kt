package org.staacks.alpharemote.feature.wificamera.data.transfer

import android.content.ContentValues
import android.content.Context
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import org.staacks.alpharemote.feature.wificamera.data.net.CameraBusyException
import org.staacks.alpharemote.feature.wificamera.data.net.NetworkHttpClient
import org.staacks.alpharemote.feature.wificamera.domain.ImageQuality
import org.staacks.alpharemote.feature.wificamera.domain.TransferItem
import org.staacks.alpharemote.feature.wificamera.domain.TransferProgress
import java.io.IOException
import java.io.OutputStream

/**
 * Pulls the photos the user selected on the camera and files them in the gallery.
 *
 * Emits progress as it goes and completes when the transfer does. Cold: nothing happens until
 * collected, and cancelling closes the session properly rather than leaving the camera waiting.
 */
class PhotoDownloader(
    private val context: Context,
    private val http: NetworkHttpClient,
    private val pushListControlUrl: String,
    private val contentDirectoryControlUrl: String,
    private val digitalImagingXml: String?
) {

    fun download(): Flow<TransferProgress> = flow {
        emit(TransferProgress.Preparing)

        val session = PushTransferSession(
            soap = SoapClient(http),
            pushListControlUrl = pushListControlUrl,
            contentDirectoryControlUrl = contentDirectoryControlUrl
        )

        var outcome = PushTransferSession.ErrorCode.ERROR
        session.start()
        try {
            val items = collectItems(session)
            if (items.isEmpty()) {
                outcome = PushTransferSession.ErrorCode.OK
                emit(TransferProgress.Finished(saved = 0, skipped = 0))
                return@flow
            }

            var saved = 0
            var skipped = 0

            items.forEachIndexed { index, item ->
                if (item.quality < ImageQuality.FULL_QUALITY_FLOOR) {
                    // The user asked for full quality. A thumbnail is not a smaller version of
                    // what they wanted, it is the wrong file.
                    Log.i(TAG, "Skipping ${item.fileName}: only ${item.quality} was offered")
                    skipped++
                    return@forEachIndexed
                }

                emit(
                    TransferProgress.Downloading(
                        completed = index,
                        total = items.size,
                        fileName = item.fileName,
                        bytesDownloaded = 0,
                        fileBytes = item.sizeBytes
                    )
                )

                val written = saveItem(item) { bytes, totalBytes ->
                    emit(
                        TransferProgress.Downloading(
                            completed = index,
                            total = items.size,
                            fileName = item.fileName,
                            bytesDownloaded = bytes,
                            fileBytes = totalBytes ?: item.sizeBytes
                        )
                    )
                }

                if (written) saved++ else skipped++
                session.reportProgress(total = items.size, transferred = index + 1)
            }

            outcome = PushTransferSession.ErrorCode.OK
            emit(TransferProgress.Finished(saved = saved, skipped = skipped))
        } catch (cancellation: CancellationException) {
            outcome = PushTransferSession.ErrorCode.CANCELLED
            throw cancellation
        } catch (error: Exception) {
            Log.w(TAG, "Transfer failed", error)
            emit(TransferProgress.Failed(error.message ?: "The transfer failed"))
        } finally {
            // The camera sits on "Connecting…" forever if this is skipped, so it runs even when
            // the coroutine is being cancelled.
            withContext(NonCancellable) { session.end(outcome) }
        }
    }.flowOn(Dispatchers.IO)

    /**
     * The camera offers its content two ways, and which one applies depends on how the user
     * selected the images on the body.
     */
    private suspend fun FlowCollector<TransferProgress>.collectItems(
        session: PushTransferSession
    ): List<TransferItem> {
        // A single selection is handed over directly in the Digital Imaging document, with no
        // browsing involved at all.
        digitalImagingXml
            ?.let { runCatching { DidlLiteParser.parseCurrentContent(it) }.getOrNull() }
            ?.let { return listOf(it) }

        val root = session.pushRoot()
        // Reported as it goes: walking a large selection takes long enough that a screen still
        // saying "Preparing" would look stuck.
        return session.listContent(root) { found -> emit(TransferProgress.Listing(found)) }
    }

    /**
     * Streams one file into MediaStore.
     *
     * Written as pending and published on completion, so a transfer interrupted halfway does not
     * leave a truncated image in the user's gallery.
     */
    private suspend fun saveItem(
        item: TransferItem,
        onProgress: suspend (Long, Long?) -> Unit
    ): Boolean {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, item.fileName)
            put(MediaStore.Images.Media.MIME_TYPE, item.fileName.mimeType())
            put(
                MediaStore.Images.Media.RELATIVE_PATH,
                "${Environment.DIRECTORY_PICTURES}/$ALBUM"
            )
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }

        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: run {
                Log.w(TAG, "Could not create a gallery entry for ${item.fileName}")
                return false
            }

        try {
            val download = openWithRetry(item.url)
            download.stream.use { input ->
                resolver.openOutputStream(uri)?.use { output ->
                    copyReportingProgress(input, output, download.contentLength, onProgress)
                } ?: throw IOException("Could not open ${item.fileName} for writing")
            }

            resolver.update(
                uri,
                ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) },
                null,
                null
            )
            return true
        } catch (error: Exception) {
            // Remove the placeholder so a failure does not leave an empty file behind. Runs even
            // under cancellation, then lets the cancellation continue.
            withContext(NonCancellable) { runCatching { resolver.delete(uri, null, null) } }
            if (error is CancellationException) throw error
            Log.w(TAG, "Failed to save ${item.fileName}", error)
            return false
        }
    }

    /** 503 means busy rather than absent, so it is worth a short wait and another attempt. */
    private suspend fun openWithRetry(url: String): NetworkHttpClient.Download {
        var lastError: IOException? = null
        repeat(BUSY_RETRIES) { attempt ->
            try {
                return http.openDownload(url)
            } catch (busy: CameraBusyException) {
                lastError = busy
                delay(BUSY_RETRY_DELAY_MS * (attempt + 1))
            }
        }
        throw lastError ?: IOException("Could not open $url")
    }

    private suspend fun copyReportingProgress(
        input: java.io.InputStream,
        output: OutputStream,
        contentLength: Long?,
        onProgress: suspend (Long, Long?) -> Unit
    ) {
        val buffer = ByteArray(BUFFER_SIZE)
        var total = 0L
        var lastReported = 0L

        while (currentCoroutineContext().isActive) {
            val read = input.read(buffer)
            if (read < 0) break
            output.write(buffer, 0, read)
            total += read

            // Throttled: a progress emission per 64 KB chunk would swamp the notification and
            // the UI without telling anyone anything new.
            if (total - lastReported >= PROGRESS_STEP_BYTES) {
                lastReported = total
                onProgress(total, contentLength)
            }
        }
        output.flush()
        onProgress(total, contentLength)
    }

    private fun String.mimeType(): String = when (substringAfterLast('.', "").uppercase()) {
        "MP4" -> "video/mp4"
        "HIF", "HEIF", "HEIC" -> "image/heif"
        else -> "image/jpeg"
    }

    private companion object {
        const val TAG = "PhotoDownloader"
        const val ALBUM = "AlphaRemote"
        const val BUFFER_SIZE = 64 * 1024
        const val PROGRESS_STEP_BYTES = 256 * 1024
        const val BUSY_RETRIES = 3
        const val BUSY_RETRY_DELAY_MS = 500L
    }
}
