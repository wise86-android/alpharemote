package org.staacks.alpharemote.ui.settings

import android.bluetooth.BluetoothDevice
import android.bluetooth.le.ScanFilter
import android.companion.AssociationRequest
import android.companion.BluetoothLeDeviceFilter
import android.companion.CompanionDeviceManager
import android.companion.ObservingDevicePresenceRequest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import org.staacks.alpharemote.MainActivity
import org.staacks.alpharemote.utils.hasBluetoothPermission

//Massive thanks to coral for the documentation of the camera's BLE protocol at
// https://github.com/coral/freemote

object CompanionDeviceHelper {

    fun getAssociation(context: Context): List<String> {
        val deviceManager = ContextCompat.getSystemService(context, CompanionDeviceManager::class.java) ?: return emptyList()

        return deviceManager.associations
    }

    fun pairCompanionDevice(context: Context, callback: CompanionDeviceManager.Callback) {
        val deviceFilter: BluetoothLeDeviceFilter = BluetoothLeDeviceFilter.Builder()
            .setScanFilter(ScanFilter.Builder()
                .setManufacturerData(
                    0x012d,                           // Sony manufacturedId
                    byteArrayOf(                    // Filter raw values
                        0x03.toByte(), 0x00.toByte(),                //  Sony Camera
                        0x64.toByte(), 0x00.toByte(),                //  Version
                        0x00.toByte(), 0x00.toByte(),                //  Model code, as utf8
                        0x22.toByte(), 0xC0.toByte(), 0x00.toByte(), //  0x80 = pairing supported, 0x40 = pairing enabled, 0x20 = location supported, 0x10 = location enabled, 0x8 = remote supported, 0x4 = remote enabled ( in 6600)
                        // present in other protocol version ?
                        //0x23.toByte(), 0x00.toByte(),0x00.toByte(), // 0x80 = remote control supported, 0x20 = remote control enabled, 0x10 = image transfer supported , 0x04 = image transfer enabled 0x2 = push transfer supported, 0x01 = push transfer enabled
                        //0x21.toByte() 0x00.toByte(),00.toByte() // 0x80 = wirless power on enabled, 0x40 = camera on, 0x20 = wifi handover supported, 0x10 = wifi handover enabled
                    ),
                    byteArrayOf(              // Filter bit mask
                        0xff.toByte(), 0xff.toByte(),               // must have the camera value
                        0x00.toByte(), 0x00.toByte(),               // we don't care about protocol version
                        0x00.toByte(), 0x00.toByte(),               // we don't care about model code
                        0xff.toByte(), 0xC0.toByte(), 0x00.toByte() // require pairing enable and supported
                    )
                )
                .build()
            )
            .build()

        val associationRequest: AssociationRequest = AssociationRequest.Builder()
            .addDeviceFilter(deviceFilter)
            .setSingleDevice(true)
            .build()

        val deviceManager = ContextCompat.getSystemService(context,CompanionDeviceManager::class.java)

        Log.d(MainActivity.TAG, "Associating.")
        deviceManager?.associate(associationRequest, callback, null)
    }

    fun startObservingDevicePresence(context: Context, device: BluetoothDevice): Boolean {
        try {
            device.createBond()
            if (context.packageManager.hasSystemFeature(PackageManager.FEATURE_COMPANION_DEVICE_SETUP)) {
                ContextCompat.getSystemService(context,CompanionDeviceManager::class.java)?.startObservingDevicePresence(
                    device.address
                )
                return true
            }
        } catch (e: SecurityException) {
            Log.e(MainActivity.TAG, e.toString())
            //This should be impossible as we check the permission before attempting to pair with the companion device.
        }
        Log.e(MainActivity.TAG, "Failed to observe device presence.")
        return false
    }

    fun unpairCompanionDevice(context: Context) {
        val deviceManager =
            ContextCompat.getSystemService(context, CompanionDeviceManager::class.java) ?: return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA) {
            for (association in deviceManager.myAssociations) {
                Log.d(MainActivity.TAG, "Disassociating ${association.deviceMacAddress}.")
                val request = ObservingDevicePresenceRequest.Builder().setAssociationId(association.id).build()
                deviceManager.stopObservingDevicePresence(request)
                if (hasBluetoothPermission(context))
                    deviceManager.removeBond(association.id)
                deviceManager.disassociate(association.id)
            }
        } else {
            for (address in deviceManager.associations) {
                Log.d(MainActivity.TAG, "Disassociating $address.")
                deviceManager.stopObservingDevicePresence(address)
                deviceManager.disassociate(address)
            }
        }
    }
}