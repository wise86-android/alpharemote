package org.staacks.alpharemote.service

import android.annotation.SuppressLint
import android.content.Context
import android.os.Looper
import android.util.Log
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import org.staacks.alpharemote.camera.CameraBLE
import org.staacks.alpharemote.camera.ble.LocationService
import org.staacks.alpharemote.data.BehaviorSettings
import org.staacks.alpharemote.utils.hasBluetoothPermission
import org.staacks.alpharemote.utils.hasLocationPermission
import kotlin.time.Duration.Companion.seconds

/**
 * Pushes the phone location to the camera - but only while the user has enabled the location
 * sync setting. Some cameras can not use the Bluetooth remote and the location link at the
 * same time, so the camera-side enable sequence must not run unless the user opted in. When
 * the setting is off (or turned off), this controller stays passive: it stops the phone GPS
 * but never writes to the camera's location characteristics.
 */
class LocationSyncController(private val context: Context) {

    private val behaviorSettings = BehaviorSettings(context)

    private val fusedLocationClient: FusedLocationProviderClient by lazy {
        LocationServices.getFusedLocationProviderClient(context)
    }

    private val locationRequest = LocationRequest.Builder(
        Priority.PRIORITY_HIGH_ACCURACY,
        20.seconds.inWholeMilliseconds
    )
        .setMinUpdateIntervalMillis(10.seconds.inWholeMilliseconds)
        .build()

    private var cameraBLE: CameraBLE? = null

    private val locationCallback: LocationCallback = object : LocationCallback() {
        override fun onLocationResult(locationResult: LocationResult) {
            val lastLocation = locationResult.lastLocation
            if (lastLocation !== null && hasBluetoothPermission(context))
                @SuppressLint("MissingPermission")
                cameraBLE?.setCameraLocation(lastLocation)
        }
    }

    /**
     * Observes the location sync setting together with the camera readiness for the lifetime
     * of [scope] (the per-connection scope of the service).
     */
    fun start(cameraBLE: CameraBLE, scope: CoroutineScope) {
        this.cameraBLE = cameraBLE
        combine(
            behaviorSettings.updateCameraLocation,
            cameraBLE.locationUpdateStatus
        ) { enabled, cameraStatus -> enabled to cameraStatus }
            .onEach { (enabled, cameraStatus) ->
                when {
                    !enabled -> stopLocationUpdates()

                    cameraStatus == LocationService.Status.CameraReady ->
                        if (hasBluetoothPermission(context)) {
                            @SuppressLint("MissingPermission")
                            cameraBLE.enableLocationSync()
                        }

                    cameraStatus == LocationService.Status.LocationUpdateEnabled ->
                        startLocationUpdates()

                    else -> stopLocationUpdates()
                }
            }.launchIn(scope)
    }

    fun stop() {
        stopLocationUpdates()
        cameraBLE = null
    }

    private fun startLocationUpdates() {
        if (hasLocationPermission(context)) {
            @SuppressLint("MissingPermission")
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                Looper.getMainLooper()
            )
            Log.d(TAG, "Location updates started.")
        } else {
            Log.d(TAG, "Location updates missing permission.")
        }
    }

    private fun stopLocationUpdates() {
        fusedLocationClient.removeLocationUpdates(locationCallback)
        Log.d(TAG, "Location updates stopped.")
    }

    companion object {
        private val TAG = LocationSyncController::class.java.simpleName
    }
}
