package org.staacks.alpharemote.feature.wificamera.domain

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive

/**
 * The camera settings this module understands.
 *
 * Sony's Camera Remote API gives every setting the same four-method shape (`get<Api>`,
 * `set<Api>`, `getAvailable<Api>`, `getSupported<Api>`) and reports it in `getEvent` as an object
 * carrying a `type` field. Both naming schemes are recorded here so the rest of the module never
 * hardcodes a method or JSON key: the event parser looks a setting up by [eventType] and the
 * repository builds its method names from [apiName].
 *
 * Bodies differ enormously in which of these they support — see the α6600 list in PROTOCOL.md
 * §2.2. An id missing from a given camera simply never shows up in the snapshot, so adding
 * entries here is safe.
 */
enum class CameraSettingId(
    val eventType: String,
    val currentKey: String,
    val candidatesKey: String,
    val apiName: String,
    val label: String
) {
    EXPOSURE_MODE("exposureMode", "currentExposureMode", "exposureModeCandidates", "ExposureMode", "Mode"),
    F_NUMBER("fNumber", "currentFNumber", "fNumberCandidates", "FNumber", "Aperture"),
    SHUTTER_SPEED("shutterSpeed", "currentShutterSpeed", "shutterSpeedCandidates", "ShutterSpeed", "Shutter"),
    ISO_SPEED_RATE("isoSpeedRate", "currentIsoSpeedRate", "isoSpeedRateCandidates", "IsoSpeedRate", "ISO"),

    /**
     * Reported as an integer index rather than a value — see [ExposureCompensationScale]. The
     * label carries the EV reading, the param carries the index the camera expects.
     */
    EXPOSURE_COMPENSATION("exposureCompensation", "currentExposureCompensation", "", "ExposureCompensation", "EV"),

    WHITE_BALANCE("whiteBalance", "currentWhiteBalanceMode", "whiteBalanceCandidates", "WhiteBalance", "WB"),
    FOCUS_MODE("focusMode", "currentFocusMode", "focusModeCandidates", "FocusMode", "Focus"),
    SHOOT_MODE("shootMode", "currentShootMode", "shootModeCandidates", "ShootMode", "Drive"),
    SELF_TIMER("selfTimer", "currentSelfTimer", "selfTimerCandidates", "SelfTimer", "Self timer"),
    FLASH_MODE("flashMode", "currentFlashMode", "flashModeCandidates", "FlashMode", "Flash"),
    STILL_SIZE("stillSize", "currentStillSize", "stillSizeCandidates", "StillSize", "Image size"),
    MOVIE_QUALITY("movieQuality", "currentMovieQuality", "movieQualityCandidates", "MovieQuality", "Movie quality"),
    STEADY_MODE("steadyMode", "currentSteadyMode", "steadyModeCandidates", "SteadyMode", "SteadyShot"),
    BEEP_MODE("beepMode", "currentBeepMode", "beepModeCandidates", "BeepMode", "Beep");

    val setMethod: String get() = "set$apiName"
    val getAvailableMethod: String get() = "getAvailable$apiName"

    companion object {
        private val byEventType = entries.associateBy { it.eventType }

        fun fromEventType(type: String): CameraSettingId? = byEventType[type]
    }
}

/**
 * One selectable value of a setting.
 *
 * [label] is what a human reads, [param] is what goes into the `set<X>` params array verbatim.
 * They differ only where the camera's wire format is not presentable — exposure compensation
 * travels as an index but reads as "+0.3" — but keeping them separate everywhere means the UI
 * never has to know which settings are special.
 */
data class CameraOption(
    val label: String,
    val param: JsonElement
) {
    companion object {
        fun of(value: String) = CameraOption(value, JsonPrimitive(value))
    }
}

/**
 * A setting as it stands on the camera right now.
 *
 * [available] is what the body will accept at this moment, which is narrower than what it
 * supports overall and shifts with the shooting mode — drive UI enablement from it, never from
 * the supported list. [writable] is false when the camera exposes the value but no setter, which
 * is common: several α6600 settings are readable but only changeable on the body.
 */
data class CameraSetting(
    val id: CameraSettingId,
    val current: CameraOption?,
    val available: List<CameraOption> = emptyList(),
    val writable: Boolean = false
)
