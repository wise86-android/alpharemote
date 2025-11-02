package org.staacks.alpharemote.ui.settings2

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.companion.AssociationInfo
import android.companion.CompanionDeviceManager
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.staacks.alpharemote.data.FeatureSettings
import java.util.Locale

class Settings2ViewModel(private val settings: FeatureSettings, private val associatedDevices: AssociatedDevices) : ViewModel() {


    val updateCameraLocation = settings.updateCameraLocation

    private val _devices = MutableStateFlow<List<AssociatedDevices.Device>>(emptyList())
    val devices: StateFlow<List<AssociatedDevices.Device>> = _devices.asStateFlow()

    init {
        refreshDevices()
    }

    fun setUpdateCameraLocation(boolean: Boolean){
        viewModelScope.launch {
            settings.setUpdateCameraLocation(boolean)
        }
    }

    fun removeDevice(address: String) {
        viewModelScope.launch {
            // In a real scenario, you'd interact with CompanionDeviceManager to disassociate the device.
            // For now, we'll just filter it from our local state.
            _devices.update { currentDevices ->
                currentDevices.filter { it.address != address }
            }
            // You might need to add a call to CompanionDeviceManager.disassociate(address) here.
            // This would require the CompanionDeviceManager instance to be accessible or passed.
        }
    }

    fun refreshDevices() {
        viewModelScope.launch {
            _devices.value = associatedDevices.loadAssociatedDevices()
        }
    }
}


interface AssociatedDevices{
    data class Device(
        val id: Int,
        val address: String,
        var name: String,
        val device: BluetoothDevice?,
        var isPaired: Boolean = false
    )

    fun loadAssociatedDevices():List<Device>
}

class AndroidAssociatedDevices(private val bleDeviceAdapter:BluetoothAdapter, private val companionDeviceManager: CompanionDeviceManager): AssociatedDevices{
    override fun loadAssociatedDevices(): List<AssociatedDevices.Device> =
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            postApi33_loadAssociatedDevices()
        }else{
            preApi33_loadAssociatedDevices()
        }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun postApi33_loadAssociatedDevices(): List<AssociatedDevices.Device> {
        return companionDeviceManager.myAssociations.map(this::toAssociatedDevice)
    }


    @Suppress("DEPRECATION")
    private  fun preApi33_loadAssociatedDevices(): List<AssociatedDevices.Device> {
        return companionDeviceManager.associations.map {
            AssociatedDevices.Device(
                id = -1,
                address = it.uppercase(Locale.getDefault()),
                name = bleDeviceAdapter.getRemoteDevice(it.uppercase()).name ?: "N/A",
                device = null,
            )
        }
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun toAssociatedDevice(origDevice:AssociationInfo) = AssociatedDevices.Device(
        id = origDevice.id,
        address = origDevice.deviceMacAddress?.toString().let { it?.uppercase(Locale.getDefault()) } ?: "N/A",
        name = origDevice.displayName?.ifBlank { "N/A" }?.toString() ?: "N/A",
        device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            origDevice.associatedDevice?.bleDevice?.device
        } else {
            null
        }
    )
}
