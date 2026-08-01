package org.staacks.alpharemote.feature.wificamera.ui

import androidx.work.WorkInfo
import org.staacks.alpharemote.feature.wificamera.work.PhotoDownloadWorker

/**
 * The transfer as the screen sees it.
 *
 * Derived from WorkManager rather than from a flow the screen owns, so the progress survives the
 * screen being closed and reopened — the transfer keeps running either way, and the UI is just a
 * window onto it.
 */
sealed interface DownloadUiState {

    data object Idle : DownloadUiState

    data class Running(
        val stage: String,
        val fileName: String?,
        /** Null while listing, when the total is not yet known. */
        val overallFraction: Float?
    ) : DownloadUiState

    data class Finished(val saved: Int, val skipped: Int) : DownloadUiState

    data class Failed(val message: String) : DownloadUiState
}

/**
 * Reads the worker's progress and result.
 *
 * Progress data is only present while the worker runs; the counts move to the output data when it
 * finishes, so both are read.
 */
internal fun WorkInfo?.toDownloadUiState(): DownloadUiState {
    if (this == null) return DownloadUiState.Idle

    return when (state) {
        WorkInfo.State.RUNNING, WorkInfo.State.ENQUEUED -> {
            val data = progress
            when (data.getString(PhotoDownloadWorker.KEY_STAGE)) {
                PhotoDownloadWorker.STAGE_LISTING -> DownloadUiState.Running(
                    stage = "Listing photos (${data.getInt(PhotoDownloadWorker.KEY_FOUND, 0)})…",
                    fileName = null,
                    overallFraction = null
                )

                PhotoDownloadWorker.STAGE_DOWNLOADING -> {
                    val completed = data.getInt(PhotoDownloadWorker.KEY_COMPLETED, 0)
                    val total = data.getInt(PhotoDownloadWorker.KEY_TOTAL, 0)
                    DownloadUiState.Running(
                        stage = "Downloading ${completed + 1} of $total",
                        fileName = data.getString(PhotoDownloadWorker.KEY_FILE_NAME),
                        overallFraction =
                            data.getFloat(PhotoDownloadWorker.KEY_OVERALL_FRACTION, 0f)
                    )
                }

                else -> DownloadUiState.Running(
                    stage = "Preparing…",
                    fileName = null,
                    overallFraction = null
                )
            }
        }

        WorkInfo.State.SUCCEEDED -> DownloadUiState.Finished(
            saved = outputData.getInt(PhotoDownloadWorker.KEY_SAVED, 0),
            skipped = outputData.getInt(PhotoDownloadWorker.KEY_SKIPPED, 0)
        )

        WorkInfo.State.FAILED -> DownloadUiState.Failed(
            outputData.getString(PhotoDownloadWorker.KEY_MESSAGE)
                ?: "The transfer did not complete."
        )

        WorkInfo.State.CANCELLED -> DownloadUiState.Idle
        WorkInfo.State.BLOCKED -> DownloadUiState.Running("Waiting…", null, null)
    }
}
