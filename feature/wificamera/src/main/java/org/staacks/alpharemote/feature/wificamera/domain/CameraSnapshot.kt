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
    /**
     * Whatever the camera reports about autofocus, verbatim — typically `Focused`, `Focusing` or
     * `Not Focused`.
     *
     * Null on bodies that never mention it, which includes the α6600: it rejects
     * `setLiveviewFrameInfo`, so no AF boxes arrive on the live view stream either
     * (PROTOCOL.md §3.3). Treat a null as "this camera does not say", not as "not focused".
     */
    val focusStatus: String? = null,
    /** Postview JPEG of the most recent shot, including shots taken on the body itself. */
    val latestPostviewUrl: String? = null
) {
    operator fun get(id: CameraSettingId): CameraSetting? = settings[id]

    /**
     * Whether to offer the shutter.
     *
     * Driven by the camera's own list of callable methods, not by [status]. `cameraStatus` is not
     * dependably `IDLE` on every body at every moment — several report other states while live
     * view runs — and gating on it leaves a dead button for reasons unrelated to whether a shot is
     * possible. The API list is the camera's actual statement of what it will accept.
     *
     * Deliberately permissive: an empty API list means the camera has not said, so the shutter
     * stays live and the camera gets to refuse. A refusal with a reason beats a button that does
     * nothing and explains nothing.
     */
    val canShoot: Boolean
        get() = (availableApis.isEmpty() || CAPTURE_METHOD in availableApis) && !status.isBusy

    companion object {
        const val CAPTURE_METHOD = "actTakePicture"
    }
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

    /**
     * States in which the camera is working on something and a second shutter release would be
     * rejected. [UNKNOWN] is not among them: never having been told is not a reason to refuse.
     */
    val isBusy: Boolean
        get() = this in setOf(
            NOT_READY,
            STILL_CAPTURING,
            STILL_POST_PROCESSING,
            STILL_SAVING,
            MOVIE_SAVING,
            CONTENTS_TRANSFER,
            DELETING,
            EDITING,
            ERROR
        )

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
