package org.staacks.alpharemote.ui.camera


import android.view.MotionEvent
import org.staacks.alpharemote.service.AlphaRemoteRepository
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.staacks.alpharemote.camera.CameraState
import org.staacks.alpharemote.camera.CameraAction
import org.staacks.alpharemote.service.AlphaRemoteService
import org.staacks.alpharemote.service.ServiceState


class CameraViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = AlphaRemoteRepository.getInstance(application)

    data class CameraUIState (
        var connected: Boolean = false,
        var serviceState: ServiceState.Running? = null,
        var cameraState: CameraState.Ready? = null,

        var bulbToggle: Boolean = false,
        var bulbDuration: Double? = 5.0,
        var intervalToggle: Boolean = false,
        var intervalCount: Int? = 50,
        var intervalDuration: Double? = 3.0,
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

    init {
        viewModelScope.launch {
            repository.serviceState.collectLatest {
                (it as? ServiceState.Running)?.also { serviceRunning ->
                    _uiState.value = uiState.value.copy(
                        serviceState = serviceRunning,
                        cameraState = (serviceRunning.cameraState as? CameraState.Ready),
                        connected = (serviceRunning.cameraState is CameraState.Ready)
                    )
                } ?: run {
                    _uiState.value = uiState.value.copy(serviceState = null, cameraState = null, connected = false)
                }
            }
        }
    }



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
            state.bulbDuration?.toFloat() ?: 5.0f,
            state.intervalCount ?: 50,
            state.intervalDuration?.toFloat() ?: 3.0f
        )
    }

}