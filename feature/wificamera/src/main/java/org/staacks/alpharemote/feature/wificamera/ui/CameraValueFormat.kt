package org.staacks.alpharemote.feature.wificamera.ui

import org.staacks.alpharemote.feature.wificamera.domain.CameraSettingId

/**
 * Turns protocol values into what a camera back would show.
 *
 * Kept apart from the composables and free of Android types so the mapping can be tested — the
 * exposure mode abbreviations in particular are guesswork about Sony's wording until they are
 * seen on real bodies.
 */
object CameraValueFormat {

    private const val MISSING = "--"

    /** Sony spells exposure modes out; a camera back shows one or two letters. */
    private val EXPOSURE_MODE_CODES = mapOf(
        "program auto" to "P",
        "program" to "P",
        "aperture" to "A",
        "aperture priority" to "A",
        "shutter" to "S",
        "shutter priority" to "S",
        "manual" to "M",
        "intelligent auto" to "iA",
        "superior auto" to "SA",
        "movie" to "MOV",
        "panorama" to "PAN",
        "scene selection" to "SCN"
    )

    /**
     * The value as it appears on a chip.
     *
     * Only aperture is decorated — the camera reports "5.6" and a photographer reads "ƒ5.6".
     * Everything else is already in its conventional form.
     */
    fun chipValue(id: CameraSettingId, label: String?): String {
        val value = label?.takeIf { it.isNotBlank() } ?: return MISSING
        return when (id) {
            CameraSettingId.F_NUMBER -> "ƒ$value"
            CameraSettingId.EXPOSURE_MODE -> exposureModeCode(value)
            else -> value
        }
    }

    /** The larger readout in the picker sheet, where there is room for a unit. */
    fun sheetValue(id: CameraSettingId, label: String?): String {
        val value = label?.takeIf { it.isNotBlank() } ?: return MISSING
        return when (id) {
            CameraSettingId.F_NUMBER -> "ƒ$value"
            CameraSettingId.EXPOSURE_COMPENSATION -> "$value EV"
            else -> value
        }
    }

    /**
     * Falls back to the raw value rather than to a placeholder: an unrecognised mode is still
     * more useful on screen than a dash, and bodies use wording this map will not have.
     */
    fun exposureModeCode(value: String): String =
        EXPOSURE_MODE_CODES[value.trim().lowercase()] ?: value.take(3).uppercase()
}
