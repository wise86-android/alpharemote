package org.staacks.alpharemote.feature.dof

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Holds the depth of field screen's state. Unlike the other view models in this app it touches no
 * service or repository: the calculator is offline maths over user input.
 *
 * [DofSettings] is the single source of truth. Every setter writes through to the store and the
 * state comes back out of its flow, so nothing is cached here.
 */
class DofViewModel(application: Application) : AndroidViewModel(application) {

    private val settings = DofSettings(application)

    data class DofUIState(
        val input: DofSettings.DofInput,
        val result: DofResult
    )

    val uiState: StateFlow<DofUIState> = settings.input
        .map { it.toUIState() }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            DofSettings.DEFAULT_INPUT.toUIState()
        )

    fun updateDistance(meters: Float) {
        viewModelScope.launch {
            settings.setDistanceMeters(meters.coerceIn(DISTANCE_RANGE_M))
        }
    }

    fun updateFocalLength(mm: Float) {
        viewModelScope.launch {
            settings.setFocalLengthMm(mm.coerceIn(FOCAL_LENGTH_RANGE_MM))
        }
    }

    fun updateAperture(aperture: Float) {
        viewModelScope.launch { settings.setAperture(aperture) }
    }

    fun updateSensor(sensor: SensorType) {
        viewModelScope.launch { settings.setSensor(sensor) }
    }

    private fun DofSettings.DofInput.toUIState() = DofUIState(
        input = this,
        result = calculateDof(
            distanceMm = distanceMeters * 1000f,
            focalLengthMm = focalLengthMm,
            aperture = aperture,
            sensor = sensor
        )
    )
}
