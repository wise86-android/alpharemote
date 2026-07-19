package org.staacks.alpharemote.ui.camera


import android.view.MotionEvent
import org.staacks.alpharemote.service.AlphaRemoteRepository
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.staacks.alpharemote.camera.CameraState
import org.staacks.alpharemote.camera.CameraAction
import org.staacks.alpharemote.data.AppearanceSettings


class CameraViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = AlphaRemoteRepository.getInstance(application)
    private val appearanceSettings = AppearanceSettings(application)

    // Immutable UI state: every change goes through update { copy(...) } so Compose and
    // StateFlow observers are notified. Text field inputs are kept as raw strings and only
    // parsed when the sequence is started.
    data class CameraUIState (
        val connected: Boolean = false,
        val cameraState: CameraState.Connected.Ready? = null,

        val bulbToggle: Boolean = false,
        val bulbDuration: String = "5.0",
        val intervalToggle: Boolean = false,
        val intervalCount: String = "50",
        val intervalDuration: String = "3.0",
    )

    sealed class CameraUIAction

    data class GenericCameraUIAction (
        val action: GenericCameraUIActionType
    ) : CameraUIAction()

    enum class GenericCameraUIActionType {
        GOTO_DEVICE_SETTINGS,
        HELP_REMOTE,
        START_ADVANCED_SEQUENCE
    }

    data class DefaultRemoteButtonCameraUIAction (
        val event: Int,
        val button: RemoteButton
    ) : CameraUIAction()

    private val _uiState = MutableStateFlow(CameraUIState())
    val uiState: StateFlow<CameraUIState> = _uiState.asStateFlow()

    private val _uiAction = MutableSharedFlow<CameraUIAction>()
    val uiAction = _uiAction.asSharedFlow()

    val customButtons: StateFlow<List<CameraAction>> = appearanceSettings.customButtonSettings
        .map { it.customButtonList ?: emptyList() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        viewModelScope.launch {
            repository.cameraState.collectLatest { state ->
                val readyState = state as? CameraState.Connected.Ready
                _uiState.update {
                    it.copy(
                        cameraState = readyState,
                        connected = readyState != null
                    )
                }
            }
        }
    }

    fun setBulbToggle(enabled: Boolean) = _uiState.update { it.copy(bulbToggle = enabled) }

    fun setBulbDuration(value: String) = _uiState.update { it.copy(bulbDuration = value) }

    fun setIntervalToggle(enabled: Boolean) = _uiState.update { it.copy(intervalToggle = enabled) }

    fun setIntervalCount(value: String) = _uiState.update { it.copy(intervalCount = value) }

    fun setIntervalDuration(value: String) = _uiState.update { it.copy(intervalDuration = value) }

    fun gotoDeviceSettings() {
        viewModelScope.launch {
            _uiAction.emit(GenericCameraUIAction(GenericCameraUIActionType.GOTO_DEVICE_SETTINGS))
        }
    }

    fun helpRemote() {
        viewModelScope.launch {
            _uiAction.emit(GenericCameraUIAction(GenericCameraUIActionType.HELP_REMOTE))
        }
    }

    fun onDefaultRemoteButtonTouch(button: RemoteButton, action: Int): Boolean {
        if (action in arrayOf(MotionEvent.ACTION_UP, MotionEvent.ACTION_DOWN, MotionEvent.ACTION_CANCEL)) {
            val cameraAction = button.toCameraAction()
            repository.sendCameraAction(cameraAction, action)
        }

        return true
    }

    fun onCustomButtonClick(action: CameraAction) {
        repository.sendCameraAction(action, null)
    }

    fun startAdvancedSequence() {
        val state = uiState.value
        repository.startAdvancedSequence(
            state.bulbDuration.toFloatOrNull() ?: 5.0f,
            state.intervalCount.toIntOrNull() ?: 50,
            state.intervalDuration.toFloatOrNull() ?: 3.0f
        )
    }

}
