package org.staacks.alpharemote.service

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.companion.CompanionDeviceService
import android.companion.DevicePresenceEvent
import android.util.Log
import org.staacks.alpharemote.MainActivity
import org.staacks.alpharemote.utils.hasBluetoothPermission

class CompanionAlphaRemoteService : CompanionDeviceService() {

    override fun onDevicePresenceEvent(event: DevicePresenceEvent) {
        super.onDevicePresenceEvent(event)
        val associationId = event.associationId
        val eventType = event.event
        
        Log.d(TAG, "onDevicePresenceEvent: associationId=$associationId, eventType=$eventType")

        val associationInfo = getSystemService(android.companion.CompanionDeviceManager::class.java).myAssociations.find { it.id == associationId }
        val device = associationInfo?.associatedDevice?.bleDevice?.device
            ?: associationInfo?.deviceMacAddress?.let { mac ->
                // associatedDevice is not necessarily available for restored associations
                // (e.g. after a reboot), so fall back to resolving the device from the stored
                // MAC address. getRemoteDevice requires the address in uppercase.
                getSystemService(BluetoothManager::class.java)
                    ?.adapter?.getRemoteDevice(mac.toString().uppercase())
            }

        if (device == null) {
            Log.w(TAG, "Device not found for associationId=$associationId")
            return
        }

        when (eventType) {
            DevicePresenceEvent.EVENT_BLE_APPEARED,
            DevicePresenceEvent.EVENT_BT_CONNECTED -> {
                handleDeviceAppeared(device)
            }
            DevicePresenceEvent.EVENT_BLE_DISAPPEARED,
            DevicePresenceEvent.EVENT_BT_DISCONNECTED -> {
                handleDeviceDisappeared(device)
            }
        }
    }

    private fun handleDeviceAppeared(address: BluetoothDevice) {
        Log.d(TAG, "Device appeared: $address")
        if (!hasBluetoothPermission(this)) {
            Log.w(MainActivity.TAG, "Missing Bluetooth permission.")
            return
        }
        try {
            AlphaRemoteService.sendConnectIntent(this,address )
        } catch (e: Exception) {
            Log.e(TAG, "Error starting service: $e")
        }
    }

    private fun handleDeviceDisappeared(address: BluetoothDevice) {
        Log.d(TAG, "Device disappeared: $address")
        AlphaRemoteService.sendDisconnectIntent(this, address)
    }

    companion object {
        const val TAG = "CompanionAlphaRemoteService"
    }
}
