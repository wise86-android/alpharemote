package org.staacks.alpharemote.ui.settings2

import android.bluetooth.BluetoothManager
import android.companion.CompanionDeviceManager
import android.content.Context
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import org.staacks.alpharemote.data.SettingsStore
import org.staacks.alpharemote.data.FeatureSettings // Assuming FeatureSettings is in data package

class Settings2ViewModelFactory(
    private val context: Context
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(Settings2ViewModel::class.java)) {
            // Get system services
            val bluetoothManager = ContextCompat.getSystemService(context, BluetoothManager::class.java)
            val bluetoothAdapter = bluetoothManager?.adapter
                ?: throw IllegalStateException("BluetoothAdapter not available")

            val companionDeviceManager = ContextCompat.getSystemService(context, CompanionDeviceManager::class.java)
                ?: throw IllegalStateException("CompanionDeviceManager not available")

            // Create dependencies
            val settingsStore: FeatureSettings = SettingsStore(context) // SettingsStore implements FeatureSettings
            val androidAssociatedDevices: AssociatedDevices = AndroidAssociatedDevices(bluetoothAdapter, companionDeviceManager)

            return Settings2ViewModel(settingsStore, androidAssociatedDevices) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
