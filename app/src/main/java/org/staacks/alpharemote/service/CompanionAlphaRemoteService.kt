package org.staacks.alpharemote.service

import android.companion.AssociationInfo

import android.companion.CompanionDeviceService
import android.content.Intent
import android.util.Log

import org.staacks.alpharemote.MainActivity
import org.staacks.alpharemote.R
import org.staacks.alpharemote.utils.hasBluetoothPermission

class CompanionAlphaRemoteService : CompanionDeviceService() {

    @Deprecated("Deprecated in Java")
    override fun onDeviceAppeared(address: String) {
        Log.d(TAG, "Device appeared: $address")
        try {
            super.onDeviceAppeared(address) //This is abstract on Android 12
        } catch (_: AbstractMethodError) {
        }
        if (!hasBluetoothPermission(this)) {
            Log.w(MainActivity.TAG, "Missing Bluetooth permission. Launching activity instead.")
            val intent = Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                putExtra(MainActivity.NAVIGATE_TO_INTENT_EXTRA, R.id.navigation_settings)
            }
            startActivity(intent)
            stopSelf()
            return
        }
        Log.d(TAG, "Start AlphaRemoteService service")
        try {
            AlphaRemoteService.sendConnectIntent(this,address)
        }catch (e:Exception){
            Log.e(TAG, "Error starting service: $e")
        }

    }



    override fun onDeviceAppeared(associationInfo: AssociationInfo) {
        super.onDeviceAppeared(associationInfo)
        Log.d(TAG, "API33 onDeviceAppeared: $associationInfo")
    }

    override fun onDeviceDisappeared(address: String) {
        Log.d(TAG, "Device disappeared: $address")
        try {
            super.onDeviceDisappeared(address) //This is abstract on Android 12
        } catch (_: AbstractMethodError) {
        }

        AlphaRemoteService.sendDisconnectIntent(this,address)
        stopSelf()
    }

    override fun onDeviceDisappeared(associationInfo: AssociationInfo) {
        super.onDeviceDisappeared(associationInfo)
        Log.d(TAG, "API33 onDeviceDisappeared: $associationInfo")
    }

    companion object{
        const val TAG=  "CompanionAlphaRemoteService"
    }

}