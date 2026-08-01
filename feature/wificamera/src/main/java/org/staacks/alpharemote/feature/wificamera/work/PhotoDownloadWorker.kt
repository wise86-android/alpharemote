package org.staacks.alpharemote.feature.wificamera.work

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import kotlinx.coroutines.flow.collect
import org.staacks.alpharemote.feature.wificamera.data.DefaultWifiCameraRepository
import org.staacks.alpharemote.feature.wificamera.domain.TransferProgress

/**
 * Runs the photo transfer outside the screen's lifetime.
 *
 * A transfer of a full card is minutes of work, and it must survive the user leaving the app. As
 * a foreground worker it also keeps the process alive, which matters more than usual here: the
 * camera's network is held by a `WifiNetworkSpecifier` request owned by this process, and losing
 * the process loses the network the download is running on.
 *
 * The work itself lives in the repository. This class only supplies the lifetime, the
 * notification, and the progress reporting.
 */
class PhotoDownloadWorker(
    context: Context,
    parameters: WorkerParameters
) : CoroutineWorker(context, parameters) {

    private val notificationManager =
        applicationContext.getSystemService(NotificationManager::class.java)

    override suspend fun doWork(): Result {
        createChannel()
        setForeground(foregroundInfo(PREPARING_TEXT, progress = null))

        val repository = DefaultWifiCameraRepository.getInstance(applicationContext)
        var outcome: Result = Result.failure(
            workDataOf(KEY_MESSAGE to "The transfer did not start")
        )

        repository.downloadSelectedPhotos().collect { progress ->
            setProgress(progress.toData())

            when (progress) {
                is TransferProgress.Preparing ->
                    updateNotification(PREPARING_TEXT, null)

                is TransferProgress.Listing ->
                    updateNotification("Listing photos (${progress.found})…", null)

                is TransferProgress.Downloading -> updateNotification(
                    "${progress.completed + 1} of ${progress.total} — ${progress.fileName}",
                    (progress.overallFraction * 100).toInt()
                )

                is TransferProgress.Finished -> {
                    outcome = Result.success(
                        workDataOf(
                            KEY_SAVED to progress.saved,
                            KEY_SKIPPED to progress.skipped
                        )
                    )
                }

                is TransferProgress.Failed -> {
                    outcome = Result.failure(workDataOf(KEY_MESSAGE to progress.message))
                }
            }
        }

        return outcome
    }

    override suspend fun getForegroundInfo(): ForegroundInfo =
        foregroundInfo(PREPARING_TEXT, null)

    private fun foregroundInfo(text: String, progress: Int?): ForegroundInfo {
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setContentTitle("Downloading photos")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .apply {
                // Indeterminate until there is a file count to divide by; a bar that sits at zero
                // during listing reads as a stall.
                if (progress == null) setProgress(0, 0, true)
                else setProgress(100, progress, false)
            }
            .build()

        return ForegroundInfo(
            NOTIFICATION_ID,
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        )
    }

    private suspend fun updateNotification(text: String, progress: Int?) {
        // setForeground rather than notify: it keeps the worker's foreground promise alive as
        // well as refreshing what the user sees.
        runCatching { setForeground(foregroundInfo(text, progress)) }
    }

    private fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Photo downloads",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Progress while photos are copied from the camera"
            setShowBadge(false)
        }
        notificationManager?.createNotificationChannel(channel)
    }

    private fun TransferProgress.toData(): Data = when (this) {
        is TransferProgress.Preparing -> workDataOf(KEY_STAGE to STAGE_PREPARING)
        is TransferProgress.Listing -> workDataOf(
            KEY_STAGE to STAGE_LISTING,
            KEY_FOUND to found
        )

        is TransferProgress.Downloading -> workDataOf(
            KEY_STAGE to STAGE_DOWNLOADING,
            KEY_COMPLETED to completed,
            KEY_TOTAL to total,
            KEY_FILE_NAME to fileName,
            KEY_FILE_FRACTION to (fileFraction ?: -1f),
            KEY_OVERALL_FRACTION to overallFraction
        )

        is TransferProgress.Finished -> workDataOf(
            KEY_STAGE to STAGE_FINISHED,
            KEY_SAVED to saved,
            KEY_SKIPPED to skipped
        )

        is TransferProgress.Failed -> workDataOf(
            KEY_STAGE to STAGE_FAILED,
            KEY_MESSAGE to message
        )
    }

    companion object {
        const val WORK_NAME = "wificamera-photo-download"

        const val KEY_STAGE = "stage"
        const val KEY_FOUND = "found"
        const val KEY_COMPLETED = "completed"
        const val KEY_TOTAL = "total"
        const val KEY_FILE_NAME = "fileName"
        const val KEY_FILE_FRACTION = "fileFraction"
        const val KEY_OVERALL_FRACTION = "overallFraction"
        const val KEY_SAVED = "saved"
        const val KEY_SKIPPED = "skipped"
        const val KEY_MESSAGE = "message"

        const val STAGE_PREPARING = "preparing"
        const val STAGE_LISTING = "listing"
        const val STAGE_DOWNLOADING = "downloading"
        const val STAGE_FINISHED = "finished"
        const val STAGE_FAILED = "failed"

        private const val CHANNEL_ID = "wificamera-downloads"
        private const val NOTIFICATION_ID = 4211
        private const val PREPARING_TEXT = "Preparing…"

        /**
         * Starts a transfer, or leaves a running one alone.
         *
         * `KEEP` rather than `REPLACE`: the camera allows one transfer session at a time, and
         * restarting mid-flight would abandon a session the camera is still waiting on.
         */
        fun enqueue(context: Context) {
            WorkManager.getInstance(context).enqueueUniqueWork(
                WORK_NAME,
                ExistingWorkPolicy.KEEP,
                OneTimeWorkRequestBuilder<PhotoDownloadWorker>().build()
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }
}
