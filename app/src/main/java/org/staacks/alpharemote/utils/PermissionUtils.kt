package org.staacks.alpharemote.utils

import android.Manifest
import android.content.Context
import androidx.core.content.PermissionChecker

fun hasBluetoothPermission(context: Context): Boolean{
    return PermissionChecker.checkSelfPermission(
        context,
        Manifest.permission.BLUETOOTH_CONNECT
    ) == PermissionChecker.PERMISSION_GRANTED
}

fun hasLocationPermission(context: Context): Boolean{
    val backgroundLocationAccess = PermissionChecker.checkSelfPermission(
        context, Manifest.permission.ACCESS_BACKGROUND_LOCATION
    ) == PermissionChecker.PERMISSION_GRANTED
    val fineLocationGranted = PermissionChecker.checkSelfPermission(
        context, Manifest.permission.ACCESS_FINE_LOCATION
    ) == PermissionChecker.PERMISSION_GRANTED
    val coarseLocationGranted = PermissionChecker.checkSelfPermission(
        context, Manifest.permission.ACCESS_COARSE_LOCATION
    ) == PermissionChecker.PERMISSION_GRANTED
    return backgroundLocationAccess && (fineLocationGranted || coarseLocationGranted)
}