package org.staacks.alpharemote.ui.camera

import android.view.MotionEvent
import android.view.View
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import org.staacks.alpharemote.camera.CameraStateReady
import org.staacks.alpharemote.service.AlphaRemoteService
import org.staacks.alpharemote.service.ServiceRunning
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class CameraViewModel : ViewModel() {

    data class CameraUIState (
        var connected: Boolean = false,
        var serviceState: ServiceRunning? = null,
        var cameraState: CameraStateReady? = null
    )

    sealed class CameraUIAction

    data class GenericCameraUIAction (
        val action: GenericCameraUIActionType
    ) : CameraUIAction()

    enum class GenericCameraUIActionType {
        GOTO_DEVICE_SETTINGS,
        HELP_REMOTE
    }

    data class DefaultRemoteButtonCameraUIAction (
        val event: Int,
        val button: DefaultRemoteButton.Button
    ) : CameraUIAction()

    private val _uiState = MutableStateFlow(CameraUIState())
    val uiState: StateFlow<CameraUIState> = _uiState.asStateFlow()

    private val _uiAction = MutableSharedFlow<CameraUIAction>()
    val uiAction = _uiAction.asSharedFlow()

    init {
        viewModelScope.launch {
            AlphaRemoteService.serviceState.collectLatest {
                (it as? ServiceRunning)?.also { serviceRunning ->
                    _uiState.emit(uiState.value.copy(serviceState = serviceRunning, cameraState = (serviceRunning.cameraState as? CameraStateReady), connected = (serviceRunning.cameraState is CameraStateReady)))
                } ?: run {
                    _uiState.emit(uiState.value.copy(serviceState = null, cameraState = null, connected = false))
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

    fun defaultRemoteButtonOnTouchListener(view: View, event: MotionEvent): Boolean {
        if (event.action in arrayOf(MotionEvent.ACTION_UP, MotionEvent.ACTION_DOWN, MotionEvent.ACTION_CANCEL)) {
            (view as? DefaultRemoteButton)?.let {
                viewModelScope.launch {
                    _uiAction.emit(DefaultRemoteButtonCameraUIAction(event.action, it.button))
                }
            }

            //Set pressed state to show ripple effect
            view.isPressed = event.action == MotionEvent.ACTION_DOWN
        }

        return true
    }

}