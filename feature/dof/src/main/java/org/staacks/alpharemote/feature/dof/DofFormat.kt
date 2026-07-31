package org.staacks.alpharemote.feature.dof

import android.content.Context

/**
 * Localised formatting for the values shown on the depth of field screen. Kept out of [DofMath] so
 * the maths stays free of resource lookups.
 */

/** Distances below a meter read better in centimeters, so switch units at the meter mark. */
fun Context.formatDistance(mm: Float): String = when {
    mm.isInfinite() -> getString(R.string.dof_infinite)
    mm >= 1000f -> getString(R.string.dof_format_meters, mm / 1000f)
    else -> getString(R.string.dof_format_centimeters, mm / 10f)
}

fun Context.formatFocalLength(mm: Float): String = getString(R.string.dof_format_focal_length, mm)

/** Renders whole stops as "f/2" rather than "f/2.0", while keeping "f/1.8" intact. */
fun Context.formatAperture(aperture: Float): String {
    val value = if (aperture % 1f == 0f) {
        aperture.toInt().toString()
    } else {
        aperture.toString()
    }
    return getString(R.string.dof_format_aperture, value)
}
