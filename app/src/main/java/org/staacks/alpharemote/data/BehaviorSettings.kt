package org.staacks.alpharemote.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map

/**
 * Settings describing how the app behaves: whether the phone location is pushed to the camera,
 * whether external apps may control the camera via broadcasts, and the identity of the last
 * connected camera. For settings about how the app looks, see [AppearanceSettings].
 */
class BehaviorSettings(context: Context) {
    private val settings = context.settingsDataStore

    companion object {
        private val UPDATE_CAMERA_LOCATION = booleanPreferencesKey("updateCameraLocation")

        private val CAMERA_ID_NAME_KEY = stringPreferencesKey("cameraIdName")
        private val CAMERA_ID_ADDRESS_KEY = stringPreferencesKey("cameraIdAddress")

        private val BROADCAST_CONTROL_KEY = booleanPreferencesKey("broadcastControl")
    }

    val updateCameraLocation: Flow<Boolean> = settings.data.map { it[UPDATE_CAMERA_LOCATION] ?: false }

    suspend fun setUpdateCameraLocation(value: Boolean) {
        settings.edit { data ->
            data[UPDATE_CAMERA_LOCATION] = value
        }
    }

    val broadcastControl: Flow<Boolean> = settings.data.map { it[BROADCAST_CONTROL_KEY] ?: false }

    suspend fun setBroadcastControl(allow: Boolean) {
        settings.edit { data ->
            data[BROADCAST_CONTROL_KEY] = allow
        }
    }

    suspend fun getBroadcastControl(): Boolean {
        return settings.data.firstOrNull()?.get(BROADCAST_CONTROL_KEY) ?: false
    }

    suspend fun setCameraId(name: String, address: String) {
        settings.edit { data ->
            data[CAMERA_ID_NAME_KEY] = name
            data[CAMERA_ID_ADDRESS_KEY] = address
        }
    }

    suspend fun getCameraId(): Pair<String?, String?> {
        val data = settings.data.firstOrNull()
        return Pair(data?.get(CAMERA_ID_ADDRESS_KEY), data?.get(CAMERA_ID_NAME_KEY))
    }
}
