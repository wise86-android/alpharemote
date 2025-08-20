package org.staacks.alpharemote.camera.ble

import android.bluetooth.BluetoothGatt
import android.util.Log


fun logAllCharacteristics(gatt: BluetoothGatt,tag:String){
    gatt.services.forEach { service ->
        Log.d(tag, "Services: ${service.uuid}")
        service.characteristics.forEach { characteristic ->
            Log.d(tag,"\tcharacteristic: ${characteristic.uuid}")
        }
    }
}