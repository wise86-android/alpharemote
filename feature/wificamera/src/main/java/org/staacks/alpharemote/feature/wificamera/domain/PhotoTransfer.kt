package org.staacks.alpharemote.feature.wificamera.domain

/**
 * Progress of a push transfer, as it happens.
 *
 * A transfer is not a single request: the camera has to be told a session is starting, its content
 * listed and paged through, each file pulled, and the session closed. The stages are distinct
 * because they fail for different reasons and take very different amounts of time.
 */
sealed interface TransferProgress {

    /** Opening the session with the camera. */
    data object Preparing : TransferProgress

    /** Walking the camera's content listing. [found] grows as pages come in. */
    data class Listing(val found: Int) : TransferProgress

    data class Downloading(
        val completed: Int,
        val total: Int,
        val fileName: String,
        val bytesDownloaded: Long,
        val fileBytes: Long?
    ) : TransferProgress {
        /** Null when the camera did not send a Content-Length for the current file. */
        val fileFraction: Float?
            get() = fileBytes
                ?.takeIf { it > 0 }
                ?.let { (bytesDownloaded.toFloat() / it).coerceIn(0f, 1f) }

        val overallFraction: Float
            get() = if (total <= 0) 0f else (completed.toFloat() / total).coerceIn(0f, 1f)
    }

    data class Finished(val saved: Int, val skipped: Int) : TransferProgress

    data class Failed(val message: String) : TransferProgress
}

/**
 * One item offered by the camera, reduced to the rendition worth keeping.
 */
data class TransferItem(
    val title: String,
    val fileName: String,
    val url: String,
    val quality: ImageQuality,
    val sizeBytes: Long?
)

/**
 * The renditions a camera offers for one photo.
 *
 * Ordered worst to best so they can be compared. The distinction matters more than it looks:
 * the full-size original usually arrives carrying **no** profile ID at all, and treating that
 * absence as "unknown, fall back to thumbnail" silently downloads postage stamps instead of
 * photographs — a documented trap (PROTOCOL.md §4.2).
 */
enum class ImageQuality {
    THUMBNAIL,
    SMALL,
    LARGE,

    /** The untouched file as the camera stores it. */
    ORIGINAL;

    companion object {
        /**
         * Anything below this is not worth downloading when the user asked for full quality.
         */
        val FULL_QUALITY_FLOOR = LARGE
    }
}
