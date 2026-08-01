package org.staacks.alpharemote.feature.wificamera.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.staacks.alpharemote.feature.wificamera.WifiCameraDefaults
import org.staacks.alpharemote.feature.wificamera.data.DefaultWifiCameraRepository
import org.staacks.alpharemote.feature.wificamera.domain.CameraOption
import org.staacks.alpharemote.feature.wificamera.domain.CameraSetting
import org.staacks.alpharemote.feature.wificamera.domain.CameraSettingId
import org.staacks.alpharemote.feature.wificamera.domain.CameraSnapshot
import org.staacks.alpharemote.feature.wificamera.domain.WifiCameraConnection
import org.staacks.alpharemote.feature.wificamera.domain.WifiCameraRepository

/**
 * Screen state for the Wi-Fi camera.
 *
 * Holds nothing of its own: everything on screen is derived from the repository's flows, so a
 * change made on the camera body and a change made here arrive by exactly the same route. That
 * is what keeps the two from drifting apart.
 */
class WifiCameraViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: WifiCameraRepository =
        DefaultWifiCameraRepository.getInstance(application)

    data class UiState(
        val connection: WifiCameraConnection = WifiCameraConnection.Idle,
        val camera: CameraSnapshot = CameraSnapshot()
    ) {
        /** Settings the camera has actually reported, in a stable display order. */
        val settings: List<CameraSetting>
            get() = CameraSettingId.entries.mapNotNull { camera[it] }
    }

    val uiState: StateFlow<UiState> =
        combine(repository.connection, repository.camera) { connection, camera ->
            UiState(connection, camera)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UiState())

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 1)

    /** One-off failures — a rejected write, not a state the screen should keep showing. */
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    fun connect() {
        repository.connect(WifiCameraDefaults.CREDENTIALS)
    }

    fun disconnect() {
        repository.disconnect()
    }

    fun select(id: CameraSettingId, option: CameraOption) {
        viewModelScope.launch {
            repository.setSetting(id, option).onFailure { error ->
                _messages.tryEmit("${id.label}: ${error.message ?: "could not be changed"}")
            }
        }
    }
}
