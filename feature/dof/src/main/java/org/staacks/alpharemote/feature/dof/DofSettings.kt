package org.staacks.alpharemote.feature.dof

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

private const val PREFERENCES_NAME = "dof_settings"

/**
 * Standalone DataStore for the depth of field calculator. Deliberately a separate preferences file
 * from the main app's `alpharemote` store: this feature is self-contained and shares no settings
 * with the camera remote, and keeping the files apart means the module needs no access to the app's
 * internals.
 *
 * Internal rather than private so tests can reset it between cases.
 */
internal val Context.dofSettingsDataStore: DataStore<Preferences> by preferencesDataStore(name = PREFERENCES_NAME)

/**
 * The last values the user dialled in on the depth of field screen, restored on the next visit.
 */
class DofSettings(context: Context) {
    private val settings = context.dofSettingsDataStore

    companion object {
        private val DISTANCE_METERS_KEY = floatPreferencesKey("distanceMeters")
        private val FOCAL_LENGTH_MM_KEY = floatPreferencesKey("focalLengthMm")
        private val APERTURE_KEY = floatPreferencesKey("aperture")
        private val SENSOR_KEY = stringPreferencesKey("sensor")

        const val DEFAULT_DISTANCE_METERS = 1.8f
        const val DEFAULT_FOCAL_LENGTH_MM = 50f
        const val DEFAULT_APERTURE = 1.8f
        val DEFAULT_SENSOR = SensorType.FULL_FRAME

        val DEFAULT_INPUT = DofInput(
            distanceMeters = DEFAULT_DISTANCE_METERS,
            focalLengthMm = DEFAULT_FOCAL_LENGTH_MM,
            aperture = DEFAULT_APERTURE,
            sensor = DEFAULT_SENSOR
        )
    }

    data class DofInput(
        val distanceMeters: Float,
        val focalLengthMm: Float,
        val aperture: Float,
        val sensor: SensorType
    )

    /**
     * Stored values are clamped on the way out: a value written before a slider's range changed
     * would otherwise show a label the slider thumb cannot match.
     */
    val input: Flow<DofInput> = settings.data.map { data ->
        DofInput(
            distanceMeters = (data[DISTANCE_METERS_KEY] ?: DEFAULT_DISTANCE_METERS)
                .coerceIn(DISTANCE_RANGE_M),
            focalLengthMm = (data[FOCAL_LENGTH_MM_KEY] ?: DEFAULT_FOCAL_LENGTH_MM)
                .coerceIn(FOCAL_LENGTH_RANGE_MM),
            aperture = data[APERTURE_KEY] ?: DEFAULT_APERTURE,
            sensor = data[SENSOR_KEY].toSensorType()
        )
    }.distinctUntilChanged()

    suspend fun setDistanceMeters(meters: Float) {
        settings.edit { data -> data[DISTANCE_METERS_KEY] = meters }
    }

    suspend fun setFocalLengthMm(mm: Float) {
        settings.edit { data -> data[FOCAL_LENGTH_MM_KEY] = mm }
    }

    suspend fun setAperture(aperture: Float) {
        settings.edit { data -> data[APERTURE_KEY] = aperture }
    }

    suspend fun setSensor(sensor: SensorType) {
        settings.edit { data -> data[SENSOR_KEY] = sensor.name }
    }

    /**
     * Tolerates a stored value that no longer maps to a known sensor, which happens if an entry is
     * ever removed from [SensorType].
     */
    private fun String?.toSensorType(): SensorType =
        SensorType.entries.firstOrNull { it.name == this } ?: DEFAULT_SENSOR
}
