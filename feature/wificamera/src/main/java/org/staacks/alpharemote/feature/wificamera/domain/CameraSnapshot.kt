package org.staacks.alpharemote.feature.wificamera.domain

/**
 * Everything the camera has told us about itself, accumulated.
 *
 * `getEvent` reports only what changed since the last poll and leaves everything else null, so
 * this is built up by merging each response into the previous snapshot rather than replaced. It
 * is the single value the UI observes.
 */
data class CameraSnapshot(
    val status: CameraStatus = CameraStatus.UNKNOWN,
    val settings: Map<CameraSettingId, CameraSetting> = emptyMap(),
    /**
     * Methods callable *right now*. Changes with the camera's mode, so it is re-read on every
     * event rather than cached at connect time.
     */
    val availableApis: Set<String> = emptySet(),
    val liveViewActive: Boolean = false,
    val cameraFunction: String? = null,
    val storage: StorageInfo? = null,
    val battery: BatteryInfo? = null,
    /** Postview JPEG of the most recent shot, including shots taken on the body itself. */
    val latestPostviewUrl: String? = null
) {
    operator fun get(id: CameraSettingId): CameraSetting? = settings[id]

    /** True when the camera is idle enough to accept a shutter release. */
    val canShoot: Boolean get() = status == CameraStatus.IDLE
}

/** `cameraStatus` from `getEvent`, per `EnumCameraStatus`. */
enum class CameraStatus(val wireName: String) {
    UNKNOWN(""),
    IDLE("IDLE"),
    NOT_READY("NotReady"),
    STILL_CAPTURING("StillCapturing"),
    STILL_POST_PROCESSING("StillPostProcessing"),
    STILL_SAVING("StillSaving"),
    MOVIE_WAIT_REC_START("MovieWaitRecStart"),
    MOVIE_RECORDING("MovieRecording"),
    MOVIE_WAIT_REC_STOP("MovieWaitRecStop"),
    MOVIE_SAVING("MovieSaving"),
    INTERVAL_RECORDING("IntervalRecording"),
    AUDIO_RECORDING("AudioRecording"),
    CONTENTS_TRANSFER("ContentsTransfer"),
    STREAMING("Streaming"),
    DELETING("Deleting"),
    EDITING("Editing"),
    ERROR("Error");

    companion object {
        private val byWireName = entries.associateBy { it.wireName }

        fun fromWireName(name: String?): CameraStatus =
            name?.let { byWireName[it] } ?: UNKNOWN
    }
}

data class StorageInfo(
    val storageId: String?,
    val recordableImages: Int?,
    val recordableTimeMinutes: Int?
)

/**
 * Only newer `getEvent` versions report this, and only on some bodies. Null means "the camera
 * never mentioned it", not "empty".
 */
data class BatteryInfo(
    val levelPercent: Int?,
    val status: String?
)
