package org.staacks.alpharemote.ui.settings

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import org.staacks.alpharemote.data.SettingsStore
import org.staacks.alpharemote.camera.CameraAction
import org.staacks.alpharemote.camera.CameraActionPreset
import org.staacks.alpharemote.service.AlphaRemoteRepository
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.staacks.alpharemote.MainActivity
import org.staacks.alpharemote.camera.CameraState
import org.staacks.alpharemote.service.ServiceState

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = AlphaRemoteRepository.getInstance(application)
    private val settingsStore = SettingsStore(application)

    data class SettingsUIState (
        val cameraState: SettingsUICameraState = SettingsUICameraState.OFFLINE,
        val cameraError: String? = null,
        val cameraName: String? = null,
        val bluetoothEnabled: Boolean = false,
        val locationServiceEnabled: Boolean = false,
        val bleScanningEnabled: Boolean = false
    )

    enum class SettingsUICameraState {
        OFFLINE,
        CONNECTED,
        ERROR,
        NOT_ASSOCIATED,
        NOT_BONDED,
        REMOTE_DISABLED
    }

    enum class SettingsUIAction {
        PAIR,
        UNPAIR,
        ADD_CUSTOM_BUTTON,
        HELP_CONNECTION,
        HELP_CUSTOM_BUTTONS
    }

    private val _uiState = MutableStateFlow(SettingsUIState())
    val uiState: StateFlow<SettingsUIState> = _uiState.asStateFlow()

    private val _uiAction = MutableSharedFlow<SettingsUIAction>()
    val uiAction = _uiAction.asSharedFlow()

    val updateCameraLocation = settingsStore.updateCameraLocation

    fun setUpdateCameraLocation(boolean: Boolean){
        viewModelScope.launch {
            settingsStore.setUpdateCameraLocation(boolean)
        }
    }

    val buttonScaleSteps = listOf(0.6f, 0.7f, 0.85f, 1.0f, 1.15f, 1.3f, 1.5f)
    var buttonScaleIndex = MutableStateFlow(buttonScaleSteps.indexOf(1.0f))
    var broadcastControl = MutableStateFlow(false)

    private val defaultCustomButtonList = listOf(
        CameraAction(false, null, null, null, CameraActionPreset.TRIGGER_ONCE),
        CameraAction(false, 3.0f, null, null, CameraActionPreset.TRIGGER_ONCE),
        CameraAction(false, null, null, null, CameraActionPreset.RECORD),
    )

    private val _customButtonListFlow = MutableStateFlow(defaultCustomButtonList)
    val customButtonListFlow: StateFlow<List<CameraAction>> = _customButtonListFlow.asStateFlow()

    init {
        viewModelScope.launch {
            settingsStore.getNotificationButtonSize()?.let {
                val i = buttonScaleSteps.indexOf(it)
                if (i >= 0)
                    buttonScaleIndex.value = i
            }

            var customButtonList = settingsStore.getCustomButtonList()
            if (customButtonList == null) {
                customButtonList = defaultCustomButtonList
                settingsStore.saveCustomButtonList(defaultCustomButtonList)
            }
            _customButtonListFlow.value = customButtonList

            broadcastControl.value = settingsStore.getBroadcastControl()
        }

        viewModelScope.launch {
            repository.bluetoothEnabled.collectLatest { enabled ->
                _uiState.update { it.copy(bluetoothEnabled = enabled) }
            }
        }

        viewModelScope.launch {
            repository.locationEnabled.collectLatest { enabled ->
                _uiState.update { it.copy(locationServiceEnabled = enabled) }
            }
        }

        viewModelScope.launch {
            repository.bleScanningEnabled.collectLatest { enabled ->
                _uiState.update { it.copy(bleScanningEnabled = enabled) }
            }
        }

        viewModelScope.launch {
            combine(
                repository.serviceState,
                repository.associations
            ) { state, associations ->
                val (storedAddress, storedName) = settingsStore.getCameraId()
                val address = associations.firstOrNull()
                val isAssociated = address != null
                // For simplicity, we assume if it's associated, we treat it as potentially bonded or offline
                // The actual bonding state is best handled reactively by the Repository if needed.
                
                _uiState.update { currentState ->
                    when (val camState = (state as? ServiceState.Running)?.cameraState) {
                        is CameraState.Error -> currentState.copy(cameraState = SettingsUICameraState.ERROR, cameraError = camState.description)
                        is CameraState.StateNotBonded -> currentState.copy(cameraState = SettingsUICameraState.NOT_BONDED, cameraError = null, cameraName = null)
                        is CameraState.RemoteDisabled -> currentState.copy(cameraState = SettingsUICameraState.REMOTE_DISABLED, cameraError = null, cameraName = null)
                        is CameraState.Ready -> currentState.copy(cameraState = SettingsUICameraState.CONNECTED, cameraName = camState.name, cameraError = null)
                        else -> {
                            currentState.copy(
                                cameraState = if (isAssociated) SettingsUICameraState.OFFLINE else SettingsUICameraState.NOT_ASSOCIATED,
                                cameraName = if (isAssociated && storedAddress == address) storedName else null,
                                cameraError = null
                            )
                        }
                    }
                }
            }.collect()
        }
    }

    fun pair() {
        viewModelScope.launch {
            _uiAction.emit(SettingsUIAction.PAIR)
        }
    }

    fun unpair() {
        viewModelScope.launch {
            _uiAction.emit(SettingsUIAction.UNPAIR)
        }
    }

    fun reportErrorState(msg: String) {
        _uiState.update { it.copy(cameraError = msg) }
    }


    fun addCustomButton() {
        viewModelScope.launch {
            _uiAction.emit(SettingsUIAction.ADD_CUSTOM_BUTTON)
        }
    }

    fun helpConnection() {
        viewModelScope.launch {
            _uiAction.emit(SettingsUIAction.HELP_CONNECTION)
        }
    }

    fun helpCustomButtons() {
        viewModelScope.launch {
            _uiAction.emit(SettingsUIAction.HELP_CUSTOM_BUTTONS)
        }
    }

    fun setButtonScaleIndex(progressValue: Int) {
        val normalizedValue = progressValue.coerceIn(0, buttonScaleSteps.lastIndex)
        viewModelScope.launch {
            buttonScaleIndex.value = normalizedValue
            settingsStore.setNotificationButtonSize(buttonScaleSteps[normalizedValue])
        }
    }


    fun setBroadcastControl(isChecked: Boolean) {
        viewModelScope.launch {
            broadcastControl.value = isChecked
            settingsStore.setBroadcastControl(isChecked)
        }
    }

    fun removeCustomButton(index: Int) {
        val currentList = _customButtonListFlow.value
        if (index !in currentList.indices) return
        persistCustomButtonList(currentList.toMutableList().apply { removeAt(index) })
    }

    fun moveCustomButton(from: Int, to: Int) {
        val currentList = _customButtonListFlow.value
        if (from !in currentList.indices || to !in currentList.indices || from == to) return
        persistCustomButtonList(currentList.toMutableList().apply {
            val item = removeAt(from)
            add(to, item)
        })
    }

    fun updateCustomButton(index: Int, action: CameraAction) {
        val currentList = _customButtonListFlow.value
        val nextList = if (index < 0) {
            currentList + action
        } else if (index in currentList.indices) {
            currentList.toMutableList().apply { set(index, action) }
        } else {
            return
        }
        persistCustomButtonList(nextList)
    }

    private fun persistCustomButtonList(nextList: List<CameraAction>) {
        _customButtonListFlow.value = nextList
        viewModelScope.launch {
            settingsStore.saveCustomButtonList(nextList)
        }
    }
}