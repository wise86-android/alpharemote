package org.staacks.alpharemote.feature.dof

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.atan

class DofMathTest {

    /** Reference case: 50 mm at f/1.8 on full frame, subject 1.8 m away. */
    @Test
    fun computesKnownFullFrameCase() {
        val result = calculateDof(1800f, 50f, 1.8f, SensorType.FULL_FRAME)

        assertEquals(1736.61f, result.nearLimitMm, 0.1f)
        assertEquals(1868.19f, result.farLimitMm, 0.1f)
    }

    /** The same lens stopped down to f/16 keeps far more of the scene sharp. */
    @Test
    fun computesKnownStoppedDownCase() {
        val result = calculateDof(1800f, 50f, 16f, SensorType.FULL_FRAME)

        assertEquals(1361.77f, result.nearLimitMm, 0.1f)
        assertEquals(2654.14f, result.farLimitMm, 0.1f)
    }

    @Test
    fun farLimitIsInfiniteBeyondHyperfocalDistance() {
        // 24 mm at f/8 on full frame has a hyperfocal distance of roughly 2.5 m.
        val result = calculateDof(5000f, 24f, 8f, SensorType.FULL_FRAME)

        assertTrue(result.farLimitMm.isInfinite())
        assertTrue(result.nearLimitMm.isFinite())
    }

    @Test
    fun farLimitStaysFiniteWellInsideHyperfocalDistance() {
        val result = calculateDof(1000f, 24f, 8f, SensorType.FULL_FRAME)

        assertTrue(result.farLimitMm.isFinite())
    }

    /**
     * The thin lens model only describes a subject further away than the focal length; no lens can
     * focus closer than that anyway. Inside that domain the focus plane must always be sharp.
     */
    @Test
    fun focusPlaneLiesInsideTheZoneInFocus() {
        val distances = listOf(250f, 1000f, 1800f, 5000f, 10000f)
        val focalLengths = listOf(10f, 35f, 50f, 200f, 400f)

        for (distance in distances) {
            for (focalLength in focalLengths.filter { it < distance }) {
                for (aperture in APERTURES) {
                    for (sensor in SensorType.entries) {
                        val result = calculateDof(distance, focalLength, aperture, sensor)
                        val label = "$distance mm, $focalLength mm, f/$aperture, $sensor"

                        assertTrue(
                            "near limit must not exceed the focus distance ($label)",
                            result.nearLimitMm <= distance
                        )
                        assertTrue(
                            "far limit must not fall short of the focus distance ($label)",
                            result.farLimitMm >= distance
                        )
                    }
                }
            }
        }
    }

    @Test
    fun stoppingDownWidensTheZoneInFocus() {
        var previousDepth = -1f
        for (aperture in APERTURES) {
            val result = calculateDof(1800f, 50f, aperture, SensorType.FULL_FRAME)
            val depth = result.farLimitMm - result.nearLimitMm

            assertTrue("f/$aperture should not be shallower than the previous stop", depth > previousDepth)
            previousDepth = depth
        }
    }

    @Test
    fun largerCircleOfConfusionGivesMoreDepthOfField() {
        // Ordered by circle of confusion: full frame tolerates the largest blur circle.
        val depths = listOf(SensorType.MICRO_FOUR_THIRDS, SensorType.APS_C, SensorType.FULL_FRAME)
            .map {
                val result = calculateDof(1800f, 50f, 5.6f, it)
                result.farLimitMm - result.nearLimitMm
            }

        assertEquals(depths.sortedBy { it }, depths)
    }

    @Test
    fun verticalFovMatchesSensorHeightAndFocalLength() {
        for (sensor in SensorType.entries) {
            for (focalLength in listOf(10f, 50f, 400f)) {
                val expected =
                    (2 * atan(sensor.sensorHeight / (2 * focalLength)) * 180 / PI).toFloat()
                val actual = calculateDof(1800f, focalLength, 5.6f, sensor).verticalFov

                assertEquals("$sensor at $focalLength mm", expected, actual, 0.001f)
            }
        }
    }

    @Test
    fun wideAngleSeesMoreThanTelephoto() {
        val wide = calculateDof(1800f, 10f, 5.6f, SensorType.FULL_FRAME).verticalFov
        val tele = calculateDof(1800f, 400f, 5.6f, SensorType.FULL_FRAME).verticalFov

        assertTrue(wide > tele)
    }

    @Test
    fun apertureScaleIsSortedAndFreeOfDuplicates() {
        assertEquals(APERTURES.sorted(), APERTURES)
        assertEquals(APERTURES.distinct(), APERTURES)
    }
}
