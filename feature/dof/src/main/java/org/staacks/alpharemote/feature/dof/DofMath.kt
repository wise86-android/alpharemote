package org.staacks.alpharemote.feature.dof

import androidx.annotation.StringRes
import kotlin.math.PI
import kotlin.math.atan

/**
 * Depth of field maths. Deliberately free of any Compose or Android framework dependency (beyond
 * the [StringRes] label reference on [SensorType]) so it can be covered by plain JVM unit tests.
 */

/** Height of the reference person in the diagram: 72 inches. */
const val HUMAN_HEIGHT_MM = 1828.8f

/** The far end of the diagram's distance axis: 360 inches. */
const val FAR_DISTANCE_MM = 9144f

/** Full stop and third stop apertures offered by the aperture slider. */
val APERTURES = listOf(
    1.8f, 2f, 2.2f, 2.5f, 2.8f, 3.2f, 3.5f, 4f, 4.5f, 5f, 5.6f, 6.3f, 7.1f, 8f, 9f, 10f, 11f, 13f,
    14f, 16f, 18f, 20f, 22f
)

/** Subject distance the user can dial in, in meters. */
val DISTANCE_RANGE_M = 0.25f..10f

/** Focal length the user can dial in, in millimeters. */
val FOCAL_LENGTH_RANGE_MM = 10f..200f

/**
 * A sensor format, described by its circle of confusion and its height.
 *
 * Only formats relevant to this app are listed: Sony Alpha bodies are either full frame or APS-C,
 * with Micro Four Thirds kept as a point of comparison. Settings persist the enum [name], not the
 * localised label, so translating a label cannot lose the user's choice.
 */
enum class SensorType(
    val coc: Float,
    val sensorHeight: Float,
    @param:StringRes val labelRes: Int
) {
    FULL_FRAME(0.029f, 24f, R.string.dof_sensor_full_frame),
    APS_C(0.019f, 15.6f, R.string.dof_sensor_aps_c),
    MICRO_FOUR_THIRDS(0.015f, 13f, R.string.dof_sensor_mft),
}

/**
 * The near and far edges of the in-focus zone in millimeters, plus the vertical field of view in
 * degrees. [farLimitMm] is [Float.POSITIVE_INFINITY] once the subject reaches the hyperfocal
 * distance, at which point everything beyond the near limit is acceptably sharp.
 */
data class DofResult(
    val nearLimitMm: Float,
    val farLimitMm: Float,
    val verticalFov: Float
)

fun calculateDof(
    distanceMm: Float,
    focalLengthMm: Float,
    aperture: Float,
    sensor: SensorType
): DofResult {
    val hyperfocalMm = focalLengthMm + (focalLengthMm * focalLengthMm) / (aperture * sensor.coc)
    val denominatorFar = hyperfocalMm - (distanceMm - focalLengthMm)
    val farLimitMm = if (denominatorFar <= 0) {
        Float.POSITIVE_INFINITY
    } else {
        (hyperfocalMm * distanceMm) / denominatorFar
    }
    val nearLimitMm = (hyperfocalMm * distanceMm) / (hyperfocalMm + (distanceMm - focalLengthMm))
    val verticalFov = 2 * atan(sensor.sensorHeight / (2 * focalLengthMm)) * 180 / PI.toFloat()

    return DofResult(nearLimitMm, farLimitMm, verticalFov)
}
