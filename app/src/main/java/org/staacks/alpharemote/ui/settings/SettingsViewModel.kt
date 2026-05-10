package org.staacks.alpharemote.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import org.staacks.alpharemote.data.SettingsStore
import org.staacks.alpharemote.camera.CameraAction
import org.staacks.alpharemote.camera.CameraActionPreset
import org.staacks.alpharemote.service.AlphaRemoteRepository
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.staacks.alpharemote.camera.CameraState
import org.staacks.alpharemote.service.ServiceState
import org.staacks.alpharemote.service.ServiceState.Running
import org.staacks.alpharemote.utils.hasBluetoothPermission

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
        HELP_CUSTOM_BUTTONS,
        SHOW_BLUETOOTH_REQUIRED_DIALOG,
        OPEN_BLUETOOTH_SETTINGS,
        REQUEST_BLUETOOTH_PERMISSION
    }

    private val _uiAction = MutableSharedFlow<SettingsUIAction>()
    val uiAction = _uiAction.asSharedFlow()

    private val _manualError = MutableStateFlow<String?>(null)

    val uiState: StateFlow<SettingsUIState> = combine(
        repository.serviceState,
        repository.associations,
        repository.bluetoothEnabled,
        repository.locationEnabled,
        _manualError
    ) { flows ->
        val serviceState = flows[0] as ServiceState
        @Suppress("UNCHECKED_CAST")
        val associations = flows[1] as List<String>
        val btEnabled = flows[2] as Boolean
        val locEnabled = flows[3] as Boolean
        val manualError = flows[4] as? String

        val (storedAddress, storedName) = settingsStore.getCameraId()
        val address = associations.firstOrNull()
        val isAssociated = address != null

        val cameraState: SettingsUICameraState
        val cameraName: String?
        val cameraError: String?

        when (val camState = (serviceState as? Running)?.cameraState) {
            is CameraState.Error -> {
                cameraState = SettingsUICameraState.ERROR
                cameraError = camState.description
                cameraName = null
            }
            is CameraState.StateNotBonded -> {
                cameraState = SettingsUICameraState.NOT_BONDED
                cameraError = null
                cameraName = null
            }
            is CameraState.RemoteDisabled -> {
                cameraState = SettingsUICameraState.REMOTE_DISABLED
                cameraError = null
                cameraName = null
            }
            is CameraState.Ready -> {
                if (storedAddress != camState.address) {
                    viewModelScope.launch {
                        settingsStore.setCameraId(camState.name, camState.address)
                    }
                }
                cameraState = SettingsUICameraState.CONNECTED
                cameraName = camState.name
                cameraError = null
            }
            else -> {
                cameraState = if (address != null && btEnabled) SettingsUICameraState.OFFLINE else if (isAssociated) SettingsUICameraState.NOT_BONDED else SettingsUICameraState.NOT_ASSOCIATED
                cameraName = if (isAssociated && storedAddress == address) storedName else null
                cameraError = manualError
            }
        }

        SettingsUIState(
            cameraState = cameraState,
            cameraError = cameraError,
            cameraName = cameraName,
            bluetoothEnabled = btEnabled,
            locationServiceEnabled = locEnabled,
            bleScanningEnabled = true // No longer used, but keeping for compatibility in UI
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SettingsUIState())

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
    }

    fun searchNewCamera() {
        viewModelScope.launch {
            if (!uiState.value.bluetoothEnabled) {
                _uiAction.emit(SettingsUIAction.SHOW_BLUETOOTH_REQUIRED_DIALOG)
            } else if (!hasBluetoothPermission(getApplication())) {
                _uiAction.emit(SettingsUIAction.REQUEST_BLUETOOTH_PERMISSION)
            } else {
                _uiAction.emit(SettingsUIAction.PAIR)
            }
        }
    }

    fun openBluetoothSettings() {
        viewModelScope.launch {
            _uiAction.emit(SettingsUIAction.OPEN_BLUETOOTH_SETTINGS)
        }
    }

    fun unpair() {
        viewModelScope.launch {
            _uiAction.emit(SettingsUIAction.UNPAIR)
        }
    }

    fun reportErrorState(msg: String) {
        _manualError.value = msg
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
