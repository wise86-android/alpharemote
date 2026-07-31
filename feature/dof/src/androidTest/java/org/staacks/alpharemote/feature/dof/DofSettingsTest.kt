package org.staacks.alpharemote.feature.dof

import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Verifies that each DofSettings setter changes exactly the setting it is responsible for, leaving
 * the others untouched, and that unset values fall back to the documented defaults.
 */
@RunWith(AndroidJUnit4::class)
class DofSettingsTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val dofSettings = DofSettings(context)

    @Before
    fun clearDataStore() {
        runBlocking {
            context.dofSettingsDataStore.edit { it.clear() }
        }
    }

    @Test
    fun emptyStoreYieldsTheDefaults() = runTest {
        assertEquals(DofSettings.DEFAULT_INPUT, dofSettings.input.first())
    }

    @Test
    fun setDistanceChangesOnlyThatSetting() = runTest {
        dofSettings.setDistanceMeters(4.25f)

        val input = dofSettings.input.first()
        assertEquals(4.25f, input.distanceMeters, 0f)
        assertEquals(DofSettings.DEFAULT_FOCAL_LENGTH_MM, input.focalLengthMm, 0f)
        assertEquals(DofSettings.DEFAULT_APERTURE, input.aperture, 0f)
        assertEquals(DofSettings.DEFAULT_SENSOR, input.sensor)
    }

    @Test
    fun setFocalLengthChangesOnlyThatSetting() = runTest {
        dofSettings.setFocalLengthMm(135f)

        val input = dofSettings.input.first()
        assertEquals(135f, input.focalLengthMm, 0f)
        assertEquals(DofSettings.DEFAULT_DISTANCE_METERS, input.distanceMeters, 0f)
    }

    @Test
    fun setApertureChangesOnlyThatSetting() = runTest {
        dofSettings.setAperture(11f)

        val input = dofSettings.input.first()
        assertEquals(11f, input.aperture, 0f)
        assertEquals(DofSettings.DEFAULT_DISTANCE_METERS, input.distanceMeters, 0f)
    }

    @Test
    fun setSensorChangesOnlyThatSetting() = runTest {
        dofSettings.setSensor(SensorType.APS_C)

        val input = dofSettings.input.first()
        assertEquals(SensorType.APS_C, input.sensor)
        assertEquals(DofSettings.DEFAULT_APERTURE, input.aperture, 0f)
    }

    /** A focal length stored before the slider's range shrank must not outrun the slider. */
    @Test
    fun readingClampsValuesToTheirSliderRange() = runTest {
        context.dofSettingsDataStore.edit { data ->
            data[floatPreferencesKey("focalLengthMm")] = 400f
            data[floatPreferencesKey("distanceMeters")] = 99f
        }

        val input = dofSettings.input.first()
        assertEquals(FOCAL_LENGTH_RANGE_MM.endInclusive, input.focalLengthMm, 0f)
        assertEquals(DISTANCE_RANGE_M.endInclusive, input.distanceMeters, 0f)
    }

    @Test
    fun allSettingsSurviveTogether() = runTest {
        dofSettings.setDistanceMeters(2.5f)
        dofSettings.setFocalLengthMm(85f)
        dofSettings.setAperture(2.8f)
        dofSettings.setSensor(SensorType.MICRO_FOUR_THIRDS)

        assertEquals(
            DofSettings.DofInput(2.5f, 85f, 2.8f, SensorType.MICRO_FOUR_THIRDS),
            dofSettings.input.first()
        )
    }
}
