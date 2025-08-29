package org.staacks.alpharemote.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import org.staacks.alpharemote.camera.CameraAction
import org.staacks.alpharemote.camera.CameraActionPreset
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking

private const val PREFERENCES_NAME = "alpharemote"

private val Context.settings: DataStore<Preferences> by preferencesDataStore(name = PREFERENCES_NAME)


class SettingsStore(context: Context) {
    private val settings = context.settings

   companion object {

       private val UPDATE_CAMERA_LOCATION = booleanPreferencesKey("updateCameraLocation")
       private val NOTIFICATION_GRANTED_KEY = booleanPreferencesKey("notificationGranted")
       private val BLUETOOTH_GRANTED_KEY = booleanPreferencesKey("bluetoothGranted")

       private val CAMERA_ID_NAME_KEY = stringPreferencesKey("cameraIdName")
       private val CAMERA_ID_ADDRESS_KEY = stringPreferencesKey("cameraIdAddress")
       private val NOTIFICATION_BUTTON_SIZE_KEY = floatPreferencesKey("notificationButtonSize")

       private const val CUSTOM_BUTTON_LIST_BASE_KEY = "customButtonList"
       private val CUSTOM_BUTTON_LIST_SET_BY_USER_KEY =
           booleanPreferencesKey(CUSTOM_BUTTON_LIST_BASE_KEY + "_setbyuser")

       private val BROADCAST_CONTROL_KEY = booleanPreferencesKey("broadcastControl")
   }

    val handleExternalBroadcastMessage: Boolean
        get() = runBlocking { settings.data.first()[BROADCAST_CONTROL_KEY] ?: false }

    val updateCameraLocation = settings.data.map { it[UPDATE_CAMERA_LOCATION] ?: false }

    suspend fun setUpdateCameraLocation(value: Boolean) {
        settings.edit { data ->
            data[UPDATE_CAMERA_LOCATION] = value
        }
    }

    private fun setNullableFloat(data: MutablePreferences, key: Preferences.Key<Float>, value: Float?) {
        if (value == null) {
            data -= key
        } else {
            data[key] = value
        }
    }

    suspend fun setBluetoothGranted(granted: Boolean) {
        settings.edit { data ->
            data[BLUETOOTH_GRANTED_KEY] = granted
        }
    }

    suspend fun setNotificationGranted(granted: Boolean) {
        settings.edit { data ->
            data[NOTIFICATION_GRANTED_KEY] = granted
        }
    }

    suspend fun setCameraId(name: String, address: String) {
        settings.edit { data ->
            data[CAMERA_ID_NAME_KEY] = name
            data[CAMERA_ID_ADDRESS_KEY] = address
        }
    }

    suspend fun getCameraId():  Pair<String?, String?> {
        val data = settings.data.firstOrNull()
        return Pair(data?.get(CAMERA_ID_ADDRESS_KEY),data?.get(CAMERA_ID_NAME_KEY))
    }

    suspend fun setNotificationButtonSize(size: Float) {
        settings.edit { data ->
            data[NOTIFICATION_BUTTON_SIZE_KEY] = size
        }
    }

    data class Permissions (
        val bluetooth: Boolean,
        val notification: Boolean,
        val broadcastControl: Boolean
    )

    val permissions: Flow<Permissions> = settings.data.map{
        Permissions(
            it[BLUETOOTH_GRANTED_KEY] ?: false,
            it[NOTIFICATION_GRANTED_KEY] ?: false,
            it[BROADCAST_CONTROL_KEY] ?: false
        )
    }.distinctUntilChanged()

    suspend fun getNotificationButtonSize(): Float? {
        return settings.data.firstOrNull()?.get(NOTIFICATION_BUTTON_SIZE_KEY)
    }

    data class CustomButtonSettings (
        val customButtonList: List<CameraAction>?,
        val scale: Float
    )

    val customButtonSettings: Flow<CustomButtonSettings> = settings.data.map {
        CustomButtonSettings(
            assembleCameraActionList(it),
            it[NOTIFICATION_BUTTON_SIZE_KEY] ?: 1.0f
        )
    }.distinctUntilChanged()

    suspend fun saveCustomButtonList(list: List<CameraAction>) {
        settings.edit { data ->
            data[CUSTOM_BUTTON_LIST_SET_BY_USER_KEY] = true

            for ((i, item) in list.withIndex()) {
                val keyPreset = stringPreferencesKey(CUSTOM_BUTTON_LIST_BASE_KEY + "_" + i + "_preset")
                val keyToggle = booleanPreferencesKey(CUSTOM_BUTTON_LIST_BASE_KEY + "_" + i + "_toggle")
                val keySelftimer = floatPreferencesKey(CUSTOM_BUTTON_LIST_BASE_KEY + "_" + i + "_selftimer")
                val keyDuration = floatPreferencesKey(CUSTOM_BUTTON_LIST_BASE_KEY + "_" + i + "_duration")
                val keyStep = floatPreferencesKey(CUSTOM_BUTTON_LIST_BASE_KEY + "_" + i + "_step")

                data[keyPreset] = item.preset.name
                data[keyToggle] = item.toggle

                setNullableFloat(data, keySelftimer, item.selfTimer)
                setNullableFloat(data, keyDuration, item.duration)
                setNullableFloat(data, keyStep, item.step)
            }
            var i = list.count()
            while (true) {
                val keyPreset = stringPreferencesKey(CUSTOM_BUTTON_LIST_BASE_KEY + "_" + i + "_preset")
                val keyToggle = booleanPreferencesKey(CUSTOM_BUTTON_LIST_BASE_KEY + "_" + i + "_toggle")
                val keySelftimer = floatPreferencesKey(CUSTOM_BUTTON_LIST_BASE_KEY + "_" + i + "_selftimer")
                val keyDuration = floatPreferencesKey(CUSTOM_BUTTON_LIST_BASE_KEY + "_" + i + "_duration")
                val keyStep = floatPreferencesKey(CUSTOM_BUTTON_LIST_BASE_KEY + "_" + i + "_step")

                if (!data.contains(keyPreset))
                    break

                data -= keyPreset
                data -= keyToggle
                data -= keySelftimer
                data -= keyDuration
                data -= keyStep
                i++
            }
        }
    }

    private fun assembleCameraActionList(data: Preferences): List<CameraAction>? {
        if (data[CUSTOM_BUTTON_LIST_SET_BY_USER_KEY] != true)
            return null

        val list = mutableListOf<CameraAction>()
        var i = 0
        while (true) {
            val keyPreset = stringPreferencesKey(CUSTOM_BUTTON_LIST_BASE_KEY + "_" + i + "_preset")
            val keyToggle = booleanPreferencesKey(CUSTOM_BUTTON_LIST_BASE_KEY + "_" + i + "_toggle")
            val keySelftimer = floatPreferencesKey(CUSTOM_BUTTON_LIST_BASE_KEY + "_" + i + "_selftimer")
            val keyDuration = floatPreferencesKey(CUSTOM_BUTTON_LIST_BASE_KEY + "_" + i + "_duration")
            val keyStep = floatPreferencesKey(CUSTOM_BUTTON_LIST_BASE_KEY + "_" + i + "_step")

            if (!data.contains(keyPreset))
                break

            list.add(CameraAction(
                data[keyToggle] ?: false,
                data[keySelftimer],
                data[keyDuration],
                data[keyStep],
                CameraActionPreset.valueOf(data[keyPreset] ?: CameraActionPreset.STOP.name)
            ))

            i++
        }

        return list
    }

    suspend fun getCustomButtonList(): List<CameraAction>? {
        return settings.data.firstOrNull()?.let {
            assembleCameraActionList(it)
        }
    }

    suspend fun setBroadcastControl(allow: Boolean) {
        settings.edit { data ->
            data[BROADCAST_CONTROL_KEY] = allow
        }
    }

    suspend fun getBroadcastControl():  Boolean {
        return settings.data.firstOrNull()?.get(BROADCAST_CONTROL_KEY) ?: false
    }
}