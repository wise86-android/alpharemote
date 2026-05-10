package org.staacks.alpharemote.service

import android.bluetooth.BluetoothDevice
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
        val address = associationInfo?.associatedDevice?.bleDevice?.device

        if (address == null) {
            Log.w(TAG, "Address not found for associationId=$associationId")
            return
        }

        when (eventType) {
            DevicePresenceEvent.EVENT_BLE_APPEARED, 
            DevicePresenceEvent.EVENT_BT_CONNECTED -> {
                handleDeviceAppeared(address)
            }
            DevicePresenceEvent.EVENT_BLE_DISAPPEARED, 
            DevicePresenceEvent.EVENT_BT_DISCONNECTED -> {
                handleDeviceDisappeared(address)
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
